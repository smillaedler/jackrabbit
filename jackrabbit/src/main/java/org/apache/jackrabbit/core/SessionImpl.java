/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.jackrabbit.core.config.AccessManagerConfig;
import org.apache.jackrabbit.core.config.WorkspaceConfig;
import org.apache.jackrabbit.core.nodetype.NodeDefinitionImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.core.security.AMContext;
import org.apache.jackrabbit.core.security.AccessManager;
import org.apache.jackrabbit.core.security.AuthContext;
import org.apache.jackrabbit.core.security.SecurityConstants;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.SessionItemStateManager;
import org.apache.jackrabbit.core.state.UpdatableItemStateManager;
import org.apache.jackrabbit.core.state.SharedItemStateManager;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.core.version.VersionManager;
import org.apache.jackrabbit.core.xml.DocViewSAXEventGenerator;
import org.apache.jackrabbit.core.xml.ImportHandler;
import org.apache.jackrabbit.core.xml.SessionImporter;
import org.apache.jackrabbit.core.xml.SysViewSAXEventGenerator;
import org.apache.jackrabbit.core.util.Dumpable;
import org.apache.jackrabbit.core.lock.LockManager;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.NamespaceResolver;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.QName;
import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.NamespaceException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;
import javax.security.auth.Subject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A <code>SessionImpl</code> ...
 */
public class SessionImpl implements Session, Dumpable {

    private static Logger log = Logger.getLogger(SessionImpl.class);

    /**
     * prededfined action constants in checkPermission
     */
    public static final String READ_ACTION = "read";
    public static final String REMOVE_ACTION = "remove";
    public static final String ADD_NODE_ACTION = "add_node";
    public static final String SET_PROPERTY_ACTION = "set_property";

    /**
     * flag indicating whether this session is alive
     */
    protected boolean alive;

    /**
     * the repository that issued this session
     */
    protected final RepositoryImpl rep;

    /**
     * the AuthContext of this session (can be null if this
     * session was not instantiated through a login process)
     */
    protected AuthContext loginContext;

    /**
     * the Subject of this session
     */
    protected final Subject subject;

    /**
     * the user ID that was used to acquire this session
     */
    protected final String userId;

    /**
     * the attributes of this session
     */
    protected final HashMap attributes = new HashMap();

    /**
     * the node type manager
     */
    protected final NodeTypeManagerImpl ntMgr;

    /**
     * the AccessManager associated with this session
     */
    protected AccessManager accessMgr;

    /**
     * the item state mgr associated with this session
     */
    protected final SessionItemStateManager itemStateMgr;

    /**
     * the HierarchyManager associated with this session
     */
    protected final HierarchyManager hierMgr;

    /**
     * the item mgr associated with this session
     */
    protected final ItemManager itemMgr;

    /**
     * the Workspace associated with this session
     */
    protected final WorkspaceImpl wsp;

    /**
     * the transient prefix/namespace mappings with session scope
     */
    protected final LocalNamespaceMappings nsMappings;

    /**
     * The version manager for this session
     */
    protected final VersionManager versionMgr;

    /**
     * Listeners (weak references)
     */
    protected final Map listeners = new ReferenceMap(ReferenceMap.WEAK, ReferenceMap.WEAK);

    /**
     * Lock tokens
     */
    protected final Set lockTokens = new HashSet();

    /**
     * value factory
     */
    protected ValueFactory valueFactory;

    /**
     * Protected constructor.
     *
     * @param rep
     * @param loginContext
     * @param wspConfig
     * @throws AccessDeniedException if the subject of the given login context
     *                               is not granted access to the specified
     *                               workspace
     * @throws RepositoryException   if another error occurs
     */
    protected SessionImpl(RepositoryImpl rep, AuthContext loginContext,
                          WorkspaceConfig wspConfig)
            throws AccessDeniedException, RepositoryException {
        this(rep, loginContext.getSubject(), wspConfig);
        this.loginContext = loginContext;
    }

    /**
     * Protected constructor.
     *
     * @param rep
     * @param subject
     * @param wspConfig
     * @throws AccessDeniedException if the given subject is not granted access
     *                               to the specified workspace
     * @throws RepositoryException   if another error occurs
     */
    protected SessionImpl(RepositoryImpl rep, Subject subject,
                          WorkspaceConfig wspConfig)
            throws AccessDeniedException, RepositoryException {
        alive = true;
        this.rep = rep;
        Set principals = subject.getPrincipals();
        if (principals.isEmpty()) {
            String msg = "unable to instantiate Session: no principals found";
            log.error(msg);
            throw new RepositoryException(msg);
        } else {
            // use 1st principal in case there are more that one
            Principal principal = (Principal) principals.iterator().next();
            userId = principal.getName();
        }
        this.subject = subject;
        nsMappings = new LocalNamespaceMappings(rep.getNamespaceRegistry());
        ntMgr = new NodeTypeManagerImpl(rep.getNodeTypeRegistry(), getNamespaceResolver());
        String wspName = wspConfig.getName();
        wsp = createWorkspaceInstance(wspConfig,
                rep.getWorkspaceStateManager(wspName), rep, this);
        itemStateMgr = createSessionItemStateManager(wsp.getItemStateManager());
        hierMgr = itemStateMgr.getHierarchyMgr();
        itemMgr = createItemManager(itemStateMgr, hierMgr);
        accessMgr = createAccessManager(subject, hierMgr);
        versionMgr = createVersionManager(rep);
    }

    /**
     * Create the session item state manager.
     *
     * @return session item state manager
     */
    protected SessionItemStateManager createSessionItemStateManager(UpdatableItemStateManager manager) {
        return new SessionItemStateManager(rep.getRootNodeUUID(),
                manager, getNamespaceResolver());
    }

    /**
     * Creates the workspace instance backing this session.
     *
     * @param wspConfig The workspace configuration
     * @param stateMgr  The shared item state manager
     * @param rep       The repository
     * @param session   The session
     * @return An instance of the {@link WorkspaceImpl} class or an extension
     *         thereof.
     */
    protected WorkspaceImpl createWorkspaceInstance(WorkspaceConfig wspConfig,
                                                    SharedItemStateManager stateMgr,
                                                    RepositoryImpl rep,
                                                    SessionImpl session) {
        return new WorkspaceImpl(wspConfig, stateMgr, rep, session);
    }

    /**
     * Create the item manager.
     * @return item manager
     */
    protected ItemManager createItemManager(SessionItemStateManager itemStateMgr,
                                            HierarchyManager hierMgr) {
        return new ItemManager(itemStateMgr, hierMgr, this,
                ntMgr.getRootNodeDefinition(), rep.getRootNodeUUID());
    }

    /**
     * Create the version manager. If we are not using XA, we may safely use
     * the repository version manager.
     * @return version manager
     */
    protected VersionManager createVersionManager(RepositoryImpl rep)
            throws RepositoryException {
        
        return rep.getVersionManager();
    }

    /**
     * Create the access manager.
     *
     * @return access manager
     * @throws AccessDeniedException if the current subject is not granted access
     *                               to the current workspace
     * @throws RepositoryException   if the access manager cannot be instantiated
     */
    protected AccessManager createAccessManager(Subject subject,
                                                HierarchyManager hierMgr)
            throws AccessDeniedException, RepositoryException {
        AccessManagerConfig amConfig = rep.getConfig().getAccessManagerConfig();
        try {
            AMContext ctx = new AMContext(new File(rep.getConfig().getHomeDir()),
                    rep.getConfig().getFileSystem(),
                    subject,
                    hierMgr,
                    wsp.getName());
            AccessManager accessMgr = (AccessManager) amConfig.newInstance();
            accessMgr.init(ctx);
            return accessMgr;
        } catch (AccessDeniedException ade) {
            // re-throw
            throw ade;
        } catch (Exception e) {
            // wrap in RepositoryException
            String msg = "failed to instantiate AccessManager implementation: " + amConfig.getClassName();
            log.error(msg, e);
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Performs a sanity check on this session.
     *
     * @throws RepositoryException if this session has been rendered invalid
     *                             for some reason (e.g. if this session has
     *                             been closed explicitly or if it has expired)
     */
    protected void sanityCheck() throws RepositoryException {
        // check session status
        if (!alive) {
            throw new RepositoryException("this session has been closed");
        }
    }

    /**
     * Returns the <code>Subject</code> associated with this session.
     *
     * @return the <code>Subject</code> associated with this session
     */
    protected Subject getSubject() {
        return subject;
    }

    /**
     * Returns the <code>AccessManager</code> associated with this session.
     *
     * @return the <code>AccessManager</code> associated with this session
     */
    public AccessManager getAccessManager() {
        return accessMgr;
    }

    /**
     * Returns the <code>NodeTypeManager</code>.
     *
     * @return the <code>NodeTypeManager</code>
     */
    public NodeTypeManagerImpl getNodeTypeManager() {
        return ntMgr;
    }

    /**
     * Returns the <code>ItemManager</code> of this session.
     *
     * @return the <code>ItemManager</code>
     */
    public ItemManager getItemManager() {
        return itemMgr;
    }

    /**
     * Returns the <code>NamespaceResolver</code> of this session.
     *
     * @return the <code>NamespaceResolver</code> of this session
     */
    public NamespaceResolver getNamespaceResolver() {
        return nsMappings;
    }

    /**
     * Returns the <code>SessionItemStateManager</code> associated with this session.
     *
     * @return the <code>SessionItemStateManager</code> associated with this session
     */
    protected SessionItemStateManager getItemStateManager() {
        return itemStateMgr;
    }

    /**
     * Returns the <code>HierarchyManager</code> associated with this session.
     *
     * @return the <code>HierarchyManager</code> associated with this session
     */
    public HierarchyManager getHierarchyManager() {
        return hierMgr;
    }

    /**
     * Returns the <code>VersionManager</code> associated with this session.
     *
     * @return the <code>VersionManager</code> associated with this session
     */
    public VersionManager getVersionManager() {
        return versionMgr;
    }

    /**
     * Returns the names of all workspaces of this repository with respect of the
     * access rights of this session.
     *
     * @return the names of all accessible workspaces
     * @throws RepositoryException if an error occurs
     */
    protected String[] getWorkspaceNames() throws RepositoryException {
        // filter workspaces according to access rights
        ArrayList list = new ArrayList();
        String[] names = rep.getWorkspaceNames();
        for (int i = 0; i < names.length; i++) {
            try {
                if (getAccessManager().canAccess(names[i])) {
                    list.add(names[i]);
                }
            } catch (NoSuchWorkspaceException nswe) {
                // should never happen, ignore...
            }
        }
        return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Creates a workspace with the given name.
     *
     * @param workspaceName name of the new workspace
     * @throws AccessDeniedException if the current session is not allowed to
     *                               create the workspace
     * @throws RepositoryException   if a workspace with the given name
     *                               already exists or if another error occurs
     */
    protected void createWorkspace(String workspaceName)
            throws AccessDeniedException, RepositoryException {
        // @todo verify that this session has the right privileges for this operation
        rep.createWorkspace(workspaceName);
    }

    /**
     * Notify the listeners that this session is about to be closed.
     */
    protected void notifyLoggingOut() {
        // copy listeners to array to avoid ConcurrentModificationException
        SessionListener[] la = new SessionListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (SessionListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].loggingOut(this);
            }
        }
    }

    /**
     * Notify the listeners that this session has been closed.
     */
    protected void notifyLoggedOut() {
        // copy listeners to array to avoid ConcurrentModificationException
        SessionListener[] la = new SessionListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (SessionListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].loggedOut(this);
            }
        }
    }

    /**
     * Add a <code>SessionListener</code>
     *
     * @param listener the new listener to be informed on modifications
     */
    public void addListener(SessionListener listener) {
        if (!listeners.containsKey(listener)) {
            listeners.put(listener, listener);
        }
    }

    /**
     * Remove a <code>SessionListener</code>
     *
     * @param listener an existing listener
     */
    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    //--------------------------------------------------------------< Session >
    /**
     * {@inheritDoc}
     */
    public void checkPermission(String absPath, String actions)
            throws AccessControlException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        // build the set of actions to be checked
        String[] strings = actions.split(",");
        HashSet set = new HashSet();
        for (int i = 0; i < strings.length; i++) {
            set.add(strings[i]);
        }

        Path targetPath;
        try {
            targetPath = Path.create(absPath, getNamespaceResolver(), true);
        } catch (MalformedPathException mpe) {
            String msg = "invalid path: " + absPath;
            log.debug(msg, mpe);
            throw new RepositoryException(msg);
        }
        if (!targetPath.isAbsolute()) {
            throw new RepositoryException("not an absolute path: " + absPath);
        }

        ItemId targetId = null;

        /**
         * "read" action:
         * requires READ permission on target item
         */
        if (set.contains(READ_ACTION)) {
            try {
                targetId = hierMgr.resolvePath(targetPath);
                accessMgr.checkPermission(targetId, AccessManager.READ);
            } catch (PathNotFoundException pnfe) {
                // target does not exist, throw exception
                throw new AccessControlException(READ_ACTION);
            } catch (AccessDeniedException re) {
                // otherwise the RepositoryException catch clause will
                // log a warn message, which is not appropriate in this case.
                throw new AccessControlException(READ_ACTION);
            }
        }

        Path parentPath = null;
        ItemId parentId = null;

        /**
         * "add_node" action:
         * requires WRITE permission on parent item
         */
        if (set.contains(ADD_NODE_ACTION)) {
            try {
                parentPath = targetPath.getAncestor(1);
                parentId = hierMgr.resolvePath(parentPath);
                accessMgr.checkPermission(parentId, AccessManager.WRITE);
            } catch (PathNotFoundException pnfe) {
                // parent does not exist (i.e. / was specified), throw exception
                throw new AccessControlException(ADD_NODE_ACTION);
            } catch (AccessDeniedException re) {
                // otherwise the RepositoryException catch clause will
                // log a warn message, which is not appropriate in this case.
                throw new AccessControlException(ADD_NODE_ACTION);
            }
        }

        /**
         * "remove" action:
         * requires REMOVE permission on target item
         */
        if (set.contains(REMOVE_ACTION)) {
            try {
                if (targetId == null) {
                    targetId = hierMgr.resolvePath(targetPath);
                }
                accessMgr.checkPermission(targetId, AccessManager.REMOVE);
            } catch (PathNotFoundException pnfe) {
                // parent does not exist, throw exception
                throw new AccessControlException(REMOVE_ACTION);
            } catch (AccessDeniedException re) {
                // otherwise the RepositoryException catch clause will
                // log a warn message, which is not appropriate in this case.
                throw new AccessControlException(REMOVE_ACTION);
            }
        }

        /**
         * "set_property" action:
         * requires WRITE permission on parent item if property is going to be
         * added or WRITE permission on target item if property is going to be
         * modified
         */
        if (set.contains(SET_PROPERTY_ACTION)) {
            try {
                if (targetId == null) {
                    try {
                        targetId = hierMgr.resolvePath(targetPath);
                        // property does already exist,
                        // check WRITE permission on target
                        accessMgr.checkPermission(targetId, AccessManager.WRITE);
                    } catch (PathNotFoundException pnfe) {
                        // property does not exist yet,
                        // check WRITE permission on parent
                        if (parentPath == null) {
                            parentPath = targetPath.getAncestor(1);
                        }
                        if (parentId == null) {
                            parentId = hierMgr.resolvePath(parentPath);
                        }
                        accessMgr.checkPermission(parentId, AccessManager.WRITE);
                    }
                }
            } catch (PathNotFoundException pnfe) {
                // parent does not exist, throw exception
                throw new AccessControlException(SET_PROPERTY_ACTION);
            } catch (AccessDeniedException re) {
                // otherwise the RepositoryException catch clause will
                // log a warn message, which is not appropriate in this case.
                throw new AccessControlException(SET_PROPERTY_ACTION);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Workspace getWorkspace() {
        return wsp;
    }

    /**
     * {@inheritDoc}
     */
    public Session impersonate(Credentials otherCredentials)
            throws LoginException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        if (!(otherCredentials instanceof SimpleCredentials)) {
            String msg = "impersonate failed: incompatible credentials, SimpleCredentials expected";
            log.debug(msg);
            throw new RepositoryException(msg);
        }

        // set IMPERSONATOR_ATTRIBUTE attribute of given credentials
        // with subject of current session
        SimpleCredentials creds = (SimpleCredentials) otherCredentials;
        creds.setAttribute(SecurityConstants.IMPERSONATOR_ATTRIBUTE, subject);

        try {
            return rep.login(otherCredentials, getWorkspace().getName());
        } catch (NoSuchWorkspaceException nswe) {
            // should never get here...
            String msg = "impersonate failed";
            log.error(msg, nswe);
            throw new RepositoryException(msg, nswe);
        } finally {
            // make sure IMPERSONATOR_ATTRIBUTE is removed
            creds.removeAttribute(SecurityConstants.IMPERSONATOR_ATTRIBUTE);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Node getRootNode() throws RepositoryException {
        // check sanity of this session
        sanityCheck();

        return getItemManager().getRootNode();
    }

    /**
     * {@inheritDoc}
     */
    public Node getNodeByUUID(String uuid) throws ItemNotFoundException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        try {
            NodeImpl node = (NodeImpl) getItemManager().getItem(new NodeId(uuid));
            if (node.isNodeType(QName.MIX_REFERENCEABLE)) {
                return node;
            } else {
                // there is a node with that uuid but the node does not expose it
                throw new ItemNotFoundException(uuid);
            }
        } catch (AccessDeniedException ade) {
            throw new ItemNotFoundException(uuid);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Item getItem(String absPath) throws PathNotFoundException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        try {
            Path p = Path.create(absPath, getNamespaceResolver(), true);
            if (!p.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + absPath);
            }
            return getItemManager().getItem(p);
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(absPath);
        } catch (MalformedPathException mpe) {
            String msg = "invalid path:" + absPath;
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean itemExists(String absPath) throws RepositoryException {
        // check sanity of this session
        sanityCheck();

        try {
            Path p = Path.create(absPath, getNamespaceResolver(), true);
            if (!p.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + absPath);
            }
            return getItemManager().itemExists(p);
        } catch (MalformedPathException mpe) {
            String msg = "invalid path:" + absPath;
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void save()
            throws AccessDeniedException, ItemExistsException,
            ConstraintViolationException, InvalidItemStateException,
            VersionException, LockException, NoSuchNodeTypeException,
            RepositoryException {
        // check sanity of this session
        sanityCheck();

        getItemManager().getRootNode().save();
    }

    /**
     * {@inheritDoc}
     */
    public void refresh(boolean keepChanges) throws RepositoryException {
        // check sanity of this session
        sanityCheck();

        if (!keepChanges) {
            // optimization
            itemStateMgr.disposeAllTransientItemStates();
            return;
        }
        getItemManager().getRootNode().refresh(keepChanges);
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasPendingChanges() throws RepositoryException {
        // check sanity of this session
        sanityCheck();

        return itemStateMgr.hasAnyTransientItemStates();
    }

    /**
     * {@inheritDoc}
     */
    public void move(String srcAbsPath, String destAbsPath)
            throws ItemExistsException, PathNotFoundException,
            VersionException, ConstraintViolationException, LockException,
            RepositoryException {
        // check sanity of this session
        sanityCheck();

        // check paths & get node instances

        Path srcPath;
        Path.PathElement srcName;
        Path srcParentPath;
        NodeImpl targetNode;
        NodeImpl srcParentNode;
        try {
            srcPath = Path.create(srcAbsPath, getNamespaceResolver(), true);
            if (!srcPath.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + srcAbsPath);
            }
            srcName = srcPath.getNameElement();
            srcParentPath = srcPath.getAncestor(1);
            ItemImpl item = getItemManager().getItem(srcPath);
            if (!item.isNode()) {
                throw new PathNotFoundException(srcAbsPath);
            }
            targetNode = (NodeImpl) item;
            srcParentNode = (NodeImpl) getItemManager().getItem(srcParentPath);
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(srcAbsPath);
        } catch (MalformedPathException mpe) {
            String msg = srcAbsPath + ": invalid path";
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }

        Path destPath;
        Path.PathElement destName;
        Path destParentPath;
        NodeImpl destParentNode;
        try {
            destPath = Path.create(destAbsPath, getNamespaceResolver(), true);
            if (!destPath.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + destAbsPath);
            }
            if (srcPath.isAncestorOf(destPath)) {
                String msg = destAbsPath + ": invalid destination path (cannot be descendant of source path)";
                log.debug(msg);
                throw new RepositoryException(msg);
            }
            destName = destPath.getNameElement();
            destParentPath = destPath.getAncestor(1);
            destParentNode = (NodeImpl) getItemManager().getItem(destParentPath);
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(destAbsPath);
        } catch (MalformedPathException mpe) {
            String msg = destAbsPath + ": invalid path";
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }
        int ind = destName.getIndex();
        if (ind > 0) {
            // subscript in name element
            String msg = destAbsPath + ": invalid destination path (subscript in name element is not allowed)";
            log.debug(msg);
            throw new RepositoryException(msg);
        }

        // verify that both source and destination parent nodes are checked-out
        if (!srcParentNode.internalIsCheckedOut()) {
            String msg = srcAbsPath + ": cannot move a child of a checked-in node";
            log.debug(msg);
            throw new VersionException(msg);
        }
        if (!destParentNode.internalIsCheckedOut()) {
            String msg = destAbsPath + ": cannot move a target to a checked-in node";
            log.debug(msg);
            throw new VersionException(msg);
        }

        // check for name collisions

        try {
            ItemImpl item = getItemManager().getItem(destPath);
            if (!item.isNode()) {
                // there's already a property with that name
                throw new ItemExistsException(item.safeGetJCRPath());
            } else {
                // there's already a node with that name
                // check same-name sibling setting of both new and existing node
                if (!destParentNode.getDefinition().allowsSameNameSiblings()
                        || !((NodeImpl) item).getDefinition().allowsSameNameSiblings()) {
                    throw new ItemExistsException(item.safeGetJCRPath());
                }
            }
        } catch (AccessDeniedException ade) {
            // FIXME by throwing ItemExistsException we're disclosing too much information
            throw new ItemExistsException(destAbsPath);
        } catch (PathNotFoundException pnfe) {
            // no name collision
        }

        // check constraints

        // get applicable definition of target node at new location
        NodeTypeImpl nt = (NodeTypeImpl) targetNode.getPrimaryNodeType();
        NodeDefinitionImpl newTargetDef;
        try {
            newTargetDef = destParentNode.getApplicableChildNodeDefinition(destName.getName(), nt.getQName());
        } catch (RepositoryException re) {
            String msg = destAbsPath + ": no definition found in parent node's node type for new node";
            log.debug(msg);
            throw new ConstraintViolationException(msg, re);
        }
        // check protected flag of old & new parent
        if (destParentNode.getDefinition().isProtected()) {
            String msg = destAbsPath + ": cannot add a child node to a protected node";
            log.debug(msg);
            throw new ConstraintViolationException(msg);
        }
        if (srcParentNode.getDefinition().isProtected()) {
            String msg = srcAbsPath + ": cannot remove a child node from a protected node";
            log.debug(msg);
            throw new ConstraintViolationException(msg);
        }

        // check lock status
        srcParentNode.checkLock();
        destParentNode.checkLock();

        String targetUUID = ((NodeState) targetNode.getItemState()).getUUID();
        int index = srcName.getIndex();
        if (index == 0) {
            index = 1;
        }

        if (srcParentNode.isSame(destParentNode)) {
            // do rename
            destParentNode.renameChildNode(srcName.getName(), index, targetUUID, destName.getName());
        } else {
            // do move:
            // 1. remove child node entry from old parent
            NodeState srcParentState =
                    (NodeState) srcParentNode.getOrCreateTransientItemState();
            srcParentState.removeChildNodeEntry(srcName.getName(), index);
            // 2. re-parent target node
            NodeState targetState =
                    (NodeState) targetNode.getOrCreateTransientItemState();
            targetState.setParentUUID(destParentNode.internalGetUUID());
            // 3. add child node entry to new parent
            NodeState destParentState =
                    (NodeState) destParentNode.getOrCreateTransientItemState();
            destParentState.addChildNodeEntry(destName.getName(), targetUUID);
        }

        // change definition of target
        targetNode.onRedefine(newTargetDef.unwrap().getId());
    }

    /**
     * {@inheritDoc}
     */
    public ContentHandler getImportContentHandler(String parentAbsPath,
                                                  int uuidBehavior)
            throws PathNotFoundException, ConstraintViolationException,
            VersionException, LockException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        Item item;
        try {
            Path p = Path.create(parentAbsPath, getNamespaceResolver(), true);
            if (!p.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + parentAbsPath);
            }
            item = getItemManager().getItem(p);
        } catch (MalformedPathException mpe) {
            String msg = parentAbsPath + ": invalid path";
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(parentAbsPath);
        }
        if (!item.isNode()) {
            throw new PathNotFoundException(parentAbsPath);
        }
        NodeImpl parent = (NodeImpl) item;

        // verify that parent node is checked-out
        if (!parent.internalIsCheckedOut()) {
            String msg = parentAbsPath + ": cannot add a child to a checked-in node";
            log.debug(msg);
            throw new VersionException(msg);
        }

        // check protected flag of parent node
        if (parent.getDefinition().isProtected()) {
            String msg = parentAbsPath + ": cannot add a child to a protected node";
            log.debug(msg);
            throw new ConstraintViolationException(msg);
        }

        // check lock status
        parent.checkLock();

        SessionImporter importer = new SessionImporter(parent, this, uuidBehavior);
        return new ImportHandler(importer, getNamespaceResolver(), rep.getNamespaceRegistry());
    }

    /**
     * {@inheritDoc}
     */
    public void importXML(String parentAbsPath, InputStream in,
                          int uuidBehavior)
            throws IOException, PathNotFoundException, ItemExistsException,
            ConstraintViolationException, VersionException,
            InvalidSerializedDataException, LockException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        ImportHandler handler = (ImportHandler)
                getImportContentHandler(parentAbsPath, uuidBehavior);
        try {
            XMLReader parser =
                    XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser");
            parser.setContentHandler(handler);
            parser.setErrorHandler(handler);
            // being paranoid...
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes",
                    false);

            parser.parse(new InputSource(in));
        } catch (SAXException se) {
            // check for wrapped repository exception
            Exception e = se.getException();
            if (e != null && e instanceof RepositoryException) {
                throw (RepositoryException) e;
            } else {
                String msg = "failed to parse XML stream";
                log.debug(msg);
                throw new InvalidSerializedDataException(msg, se);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void exportDocumentView(String absPath, ContentHandler contentHandler,
                                   boolean skipBinary, boolean noRecurse)
            throws PathNotFoundException, SAXException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        Item item = getItem(absPath);
        if (!item.isNode()) {
            // there's a property, though not a node at the specified path
            throw new PathNotFoundException(absPath);
        }
        new DocViewSAXEventGenerator((Node) item, noRecurse, skipBinary,
                contentHandler).serialize();
    }

    /**
     * {@inheritDoc}
     */
    public void exportDocumentView(String absPath, OutputStream out,
                                   boolean skipBinary, boolean noRecurse)
            throws IOException, PathNotFoundException, RepositoryException {
        boolean indenting = false;
        OutputFormat format = new OutputFormat("xml", "UTF-8", indenting);
        XMLSerializer serializer = new XMLSerializer(out, format);
        try {
            exportDocumentView(absPath, serializer.asContentHandler(),
                    skipBinary, noRecurse);
        } catch (SAXException se) {
            throw new RepositoryException(se);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void exportSystemView(String absPath, ContentHandler contentHandler,
                                 boolean skipBinary, boolean noRecurse)
            throws PathNotFoundException, SAXException, RepositoryException {
        // check sanity of this session
        sanityCheck();

        Item item = getItem(absPath);
        if (!item.isNode()) {
            // there's a property, though not a node at the specified path
            throw new PathNotFoundException(absPath);
        }
        new SysViewSAXEventGenerator((Node) item, noRecurse, skipBinary,
                contentHandler).serialize();
    }

    /**
     * {@inheritDoc}
     */
    public void exportSystemView(String absPath, OutputStream out,
                                 boolean skipBinary, boolean noRecurse)
            throws IOException, PathNotFoundException, RepositoryException {
        boolean indenting = false;
        OutputFormat format = new OutputFormat("xml", "UTF-8", indenting);
        XMLSerializer serializer = new XMLSerializer(out, format);
        try {
            exportSystemView(absPath, serializer.asContentHandler(),
                    skipBinary, noRecurse);
        } catch (SAXException se) {
            throw new RepositoryException(se);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLive() {
        return alive;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void logout() {
        if (!alive) {
            // ignore
            return;
        }

        // notify listeners that session is about to be closed
        notifyLoggingOut();

        // dispose name resolver
        nsMappings.dispose();
        // dispose session item state manager
        itemStateMgr.dispose();
        // dispose item manager
        itemMgr.dispose();
        // dispose workspace
        wsp.dispose();

        // invalidate session
        alive = false;

        // logout JAAS subject
        if (loginContext != null) {
            try {
                loginContext.logout();
            } catch (javax.security.auth.login.LoginException le) {
                log.warn("failed to logout current subject: " + le.getMessage());
            }
            loginContext = null;
        }

        try {
            accessMgr.close();
        } catch (Exception e) {
            log.warn("error while closing AccessManager", e);
        }

        // finally notify listeners that session has been closed
        notifyLoggedOut();
    }

    /**
     * {@inheritDoc}
     */
    public Repository getRepository() {
        return rep;
    }

    /**
     * {@inheritDoc}
     */
    public ValueFactory getValueFactory()
            throws UnsupportedRepositoryOperationException, RepositoryException {
        if (valueFactory == null) {
            valueFactory = new ValueFactoryImpl();
        }
        return valueFactory;
    }

    /**
     * {@inheritDoc}
     */
    public String getUserID() {
        return userId;
    }

    /**
     * {@inheritDoc}
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     */
    public String[] getAttributeNames() {
        return (String[]) attributes.keySet().toArray(new String[attributes.size()]);
    }

    /**
     * {@inheritDoc}
     */
    public void setNamespacePrefix(String prefix, String uri)
            throws NamespaceException, RepositoryException {
        nsMappings.setNamespacePrefix(prefix, uri);
    }

    /**
     * {@inheritDoc}
     */
    public String[] getNamespacePrefixes()
            throws NamespaceException, RepositoryException {
        return nsMappings.getPrefixes();
    }

    /**
     * {@inheritDoc}
     */
    public String getNamespaceURI(String prefix)
            throws NamespaceException, RepositoryException {
        return nsMappings.getURI(prefix);
    }

    /**
     * {@inheritDoc}
     */
    public String getNamespacePrefix(String uri)
            throws NamespaceException, RepositoryException {
        return nsMappings.getPrefix(uri);
    }

    //------------------------------------------------------< locking support >
    /**
     * {@inheritDoc}
     */
    public void addLockToken(String lt) {
        addLockToken(lt, true);
    }

    /**
     * Internal implementation of {@link #addLockToken(String)}. Additionally
     * takes a parameter indicating whether the lock manager needs to be
     * informed.
     */
    public void addLockToken(String lt, boolean notify) {
        synchronized (lockTokens) {
            if (lockTokens.add(lt) && notify) {
                try {
                    getLockManager().lockTokenAdded(this, lt);
                } catch (RepositoryException e) {
                    log.error("Lock manager not available.", e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String[] getLockTokens() {
        synchronized (lockTokens) {
            String[] result = new String[lockTokens.size()];
            lockTokens.toArray(result);
            return result;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeLockToken(String lt) {
        removeLockToken(lt, true);
    }

    /**
     * Internal implementation of {@link #removeLockToken(String)}. Additionally
     * takes a parameter indicating whether the lock manager needs to be
     * informed.
     */
    public void removeLockToken(String lt, boolean notify) {
        synchronized (lockTokens) {
            if (lockTokens.remove(lt) && notify) {
                try {
                    getLockManager().lockTokenRemoved(this, lt);
                } catch (RepositoryException e) {
                    log.error("Lock manager not available.", e);
                }
            }
        }
    }

    /**
     * Return the lock manager for this session.
     * @return lock manager for this session
     */
    public LockManager getLockManager() throws RepositoryException {
        return wsp.getLockManager();
    }

    //-------------------------------------------------------------< Dumpable >
    /**
     * {@inheritDoc}
     */
    public void dump(PrintStream ps) {
        ps.print("Session: ");
        if (userId == null) {
            ps.print("unknown");
        } else {
            ps.print(userId);
        }
        ps.println(" (" + this + ")");
        ps.println();
        itemMgr.dump(ps);
        ps.println();
        itemStateMgr.dump(ps);
    }
}
