package org.apache.jackrabbit.core.lock;

import org.apache.jackrabbit.core.TransactionException;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.log4j.Logger;

import javax.jcr.RepositoryException;
import javax.jcr.lock.LockException;
import javax.transaction.Status;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Encapsulates operations that happen in an XA environment.
 */
class XAEnvironment {

    /**
     * Logger instance for this class
     */
    private static final Logger log = Logger.getLogger(XAEnvironment.class);

    /**
     * Global lock manager.
     */
    private final LockManagerImpl lockMgr;

    /**
     * Session owning this environment.
     */
    private final SessionImpl session;

    /**
     * Map of locked nodes, indexed by their (internal) id.
     */
    private final Map lockedNodesMap = new HashMap();

    /**
     * Map of unlocked nodes, indexed by their (internal) id.
     */
    private final Map unlockedNodesMap = new HashMap();

    /**
     * List of lock/unlock operations.
     */
    private final List operations = new ArrayList();

    /**
     * Operation index.
     */
    private int opIndex;

    /**
     * Current status.
     */
    private int status;

    /**
     * Create a new instance of this class.
     * @param lockMgr global lock manager
     */
    public XAEnvironment(SessionImpl session, LockManagerImpl lockMgr) {
        this.session = session;
        this.lockMgr = lockMgr;
    }

    /**
     * Reset this environment.
     */
    public void reset() {
        lockedNodesMap.clear();
        unlockedNodesMap.clear();
        operations.clear();
        opIndex = 0;
    }

    /**
     * Lock some node.
     * @param node node to lock
     * @param isDeep <code>true</code> to deep lock this node;
     *               <code>false</code> otherwise
     * @param isSessionScoped <code>true</code> if lock should be session scoped;
     *                        <code>false</code> otherwise
     * @throws LockException if node is already locked
     * @throws RepositoryException if an error occurs
     */
    public AbstractLockInfo lock(NodeImpl node, boolean isDeep, boolean isSessionScoped)
            throws LockException, RepositoryException {

        String uuid = node.internalGetUUID();

        // check negative set first
        LockInfo info = (LockInfo) unlockedNodesMap.get(uuid);
        if (info != null) {

            // if settings are compatible, this is effectively a no-op
            if (info.deep == isDeep && info.sessionScoped == isSessionScoped) {
                unlockedNodesMap.remove(uuid);
                operations.remove(info);
                return lockMgr.getLockInfo(uuid);
            }
        }

        // verify node is not already locked.
        if (isLocked(node)) {
            throw new LockException("Node locked.");
        }

        // create a new lock info for this node
        info = new LockInfo(node, new LockToken(node.internalGetUUID()),
                isSessionScoped, isDeep, node.getSession().getUserID());
        SessionImpl session = (SessionImpl) node.getSession();
        info.setLockHolder(session);
        info.setLive(true);
        session.addLockToken(info.lockToken.toString(), false);
        lockedNodesMap.put(uuid, info);
        operations.add(info);

        return info;
    }

    /**
     * Unlock some node.
     * @param node node to unlock
     * @throws LockException if the node is not locked
     * @throws RepositoryException if an error occurs
     */
    public void unlock(NodeImpl node) throws LockException, RepositoryException {
        String uuid = node.internalGetUUID();

        // check positive set first
        AbstractLockInfo info = (LockInfo) lockedNodesMap.get(uuid);
        if (info != null) {
            lockedNodesMap.remove(uuid);
            operations.remove(info);
            info.setLive(false);
        } else {
            info = getLockInfo(node);
            if (info == null || info.getUUID() != uuid) {
                throw new LockException("Node not locked.");
            } else if (info.getLockHolder() != node.getSession()) {
                throw new LockException("Node not locked by this session.");
            }
            info = new LockInfo(node, info);
            unlockedNodesMap.put(uuid, info);
            operations.add(info);
        }

    }

    /**
     * Return a flag indicating whether the specified node is locked.
     * @return <code>true</code> if this node is locked;
     *         <code>false</code> otherwise
     * @throws RepositoryException if an error occurs
     */
    public boolean isLocked(NodeImpl node) throws RepositoryException {
        AbstractLockInfo info = getLockInfo(node);
        return info != null;
    }

    /**
     * Return the most appropriate lock information for a node. This is either
     * the lock info for the node itself, if it is locked, or a lock info for
     * one of its parents, if that one is deep locked.
     * @param node node
     * @return LockInfo lock info or <code>null</code> if node is not locked
     * @throws RepositoryException if an error occurs
     */
    public AbstractLockInfo getLockInfo(NodeImpl node) throws RepositoryException {
        String uuid = node.internalGetUUID();

        // check negative set
        if (unlockedNodesMap.containsKey(uuid)) {
            return null;
        }

        // check positive set, iteratively ascending in hierarchy
        if (!lockedNodesMap.isEmpty()) {
            NodeImpl current = node;
            for (;;) {
                LockInfo info = (LockInfo) lockedNodesMap.get(current.internalGetUUID());
                if (info != null) {
                    if (info.getUUID() == uuid || info.deep) {
                        return info;
                    }
                    break;
                }
                if (current.getDepth() == 0) {
                    break;
                }
                current = (NodeImpl) current.getParent();
            }
        }
        // ask parent
        return lockMgr.getLockInfo(uuid);
    }

    /**
     * Add lock token to this environment.
     * @param lt lock token
     */
    public void addLockToken(String lt) {}

    /**
     * Remove lock token from this environment.
     * @param lt lock token
     */
    public void removeLockToken(String lt) {}

    /**
     * Prepare update. Locks global lock manager and feeds all lock/
     * unlock operations.
     */
    public void prepare() throws TransactionException {
        status = Status.STATUS_PREPARING;
        if (!operations.isEmpty()) {
            lockMgr.beginUpdate();

            try {
                while (opIndex < operations.size()) {
                    try {
                        LockInfo info = (LockInfo) operations.get(opIndex);
                        info.update();
                    } catch (RepositoryException e) {
                        throw new TransactionException("Unable to update.", e);
                    }
                    opIndex++;
                }
            } finally {
                if (opIndex < operations.size()) {
                    while (opIndex > 0) {
                        try {
                            LockInfo info = (LockInfo) operations.get(opIndex - 1);
                            info.undo();
                        } catch (RepositoryException e) {
                            log.error("Unable to undo lock operation.", e);
                        }
                        opIndex--;
                    }
                    lockMgr.cancelUpdate();
                }
            }
        }
        status = Status.STATUS_PREPARED;
    }

    /**
     * Commit changes. This will finish the update and unlock the
     * global lock manager.
     */
    public void commit() {
        int oldStatus = status;

        status = Status.STATUS_COMMITTING;
        if (oldStatus == Status.STATUS_PREPARED) {
            if (!operations.isEmpty()) {
                lockMgr.endUpdate();
                reset();
            }
        }
        status = Status.STATUS_COMMITTED;
    }

    /**
     * Rollback changes. This will undo all updates and unlock the
     * global lock manager.
     */
    public void rollback() {
        int oldStatus = status;

        status = Status.STATUS_ROLLING_BACK;
        if (oldStatus == Status.STATUS_PREPARED) {
            if (!operations.isEmpty()) {
                while (opIndex > 0) {
                    try {
                        LockInfo info = (LockInfo) operations.get(opIndex - 1);
                        info.undo();
                    } catch (RepositoryException e) {
                        log.error("Unable to undo lock operation.", e);
                    }
                    opIndex--;
                }
                lockMgr.cancelUpdate();
                reset();
            }
        }
        status = Status.STATUS_ROLLEDBACK;
    }

    /**
     * Return a flag indicating whether a lock info belongs to a different
     * XA environment.
     */
    public boolean differentXAEnv(AbstractLockInfo info) {
        if (info instanceof LockInfo) {
            LockInfo lockInfo = (LockInfo) info;
            return lockInfo.getXAEnv() != this;
        }
        return true;
    }

    /**
     * Information about a lock used inside transactions.
     */
    class LockInfo extends AbstractLockInfo {

        /**
         * Node being locked/unlocked.
         */
        private final NodeImpl node;

        /**
         * Flag indicating whether this info belongs to a unlock operation.
         */
        private boolean isUnlock;

        /**
         * Create a new instance of this class.
         * @param lockToken     lock token
         * @param sessionScoped whether lock token is session scoped
         * @param deep          whether lock is deep
         * @param lockOwner     owner of lock
         */
        public LockInfo(NodeImpl node, LockToken lockToken,
                        boolean sessionScoped, boolean deep, String lockOwner) {

            super(lockToken, sessionScoped, deep, lockOwner);

            this.node = node;
        }

        /**
         * Create a new instance of this class. Used to signal an
         * unlock operation on some existing lock information.
         */
        public LockInfo(NodeImpl node, AbstractLockInfo info) {
            super(info.lockToken, info.sessionScoped, info.deep, info.lockOwner);

            this.node = node;
            this.isUnlock = true;
        }

        /**
         * Return a flag indicating whether this info belongs to a unlock operation.
         * @return <code>true</code> if this info belongs to an unlock operation;
         *         otherwise <code>false</code>
         */
        public boolean isUnlock() {
            return isUnlock;
        }

        /**
         * Do operation.
         */
        public void update() throws LockException, RepositoryException {
            if (isUnlock) {
                lockMgr.internalUnlock(node);
            } else {
                lockMgr.internalLock(node, deep, sessionScoped);
            }
        }

        /**
         * Undo operation.
         */
        public void undo() throws LockException, RepositoryException {
            if (isUnlock) {
                lockMgr.internalLock(node, deep, sessionScoped);
            } else {
                lockMgr.internalUnlock(node);
            }
        }

        /**
         * Return parent environment.
         */
        public XAEnvironment getXAEnv() {
            return XAEnvironment.this;
        }

        /**
         * {@inheritDoc}
         * <p/>
         * As long as the XA environment is neither committed nor rolled back,
         * associated lock information is subject to change.
         */
        public boolean mayChange() {
            if (status != Status.STATUS_COMMITTED &&
                    status != Status.STATUS_ROLLEDBACK) {
                return true;
            }
            return super.mayChange();
        }
    }
}
