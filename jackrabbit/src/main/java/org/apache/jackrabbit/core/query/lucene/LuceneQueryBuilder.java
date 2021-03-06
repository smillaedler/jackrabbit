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
package org.apache.jackrabbit.core.query.lucene;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.query.AndQueryNode;
import org.apache.jackrabbit.core.query.DerefQueryNode;
import org.apache.jackrabbit.core.query.ExactQueryNode;
import org.apache.jackrabbit.core.query.LocationStepQueryNode;
import org.apache.jackrabbit.core.query.NodeTypeQueryNode;
import org.apache.jackrabbit.core.query.NotQueryNode;
import org.apache.jackrabbit.core.query.OrQueryNode;
import org.apache.jackrabbit.core.query.OrderQueryNode;
import org.apache.jackrabbit.core.query.PathQueryNode;
import org.apache.jackrabbit.core.query.PropertyTypeRegistry;
import org.apache.jackrabbit.core.query.QueryConstants;
import org.apache.jackrabbit.core.query.QueryNode;
import org.apache.jackrabbit.core.query.QueryNodeVisitor;
import org.apache.jackrabbit.core.query.QueryRootNode;
import org.apache.jackrabbit.core.query.RelationQueryNode;
import org.apache.jackrabbit.core.query.TextsearchQueryNode;
import org.apache.jackrabbit.core.query.lucene.fulltext.QueryParser;
import org.apache.jackrabbit.core.query.lucene.fulltext.ParseException;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.name.IllegalNameException;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.NoPrefixDeclaredException;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.UnknownPrefixException;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.xerces.util.XMLChar;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.query.InvalidQueryException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Implements a query builder that takes an abstract query tree and creates
 * a lucene {@link org.apache.lucene.search.Query} tree that can be executed
 * on an index.
 * todo introduce a node type hierarchy for efficient translation of NodeTypeQueryNode
 */
public class LuceneQueryBuilder implements QueryNodeVisitor {

    /**
     * Logger for this class
     */
    private static final Logger log = Logger.getLogger(LuceneQueryBuilder.class);

    /**
     * QName for jcr:primaryType
     */
    private static QName primaryType = QName.JCR_PRIMARYTYPE;

    /**
     * QName for jcr:mixinTypes
     */
    private static QName mixinTypes = QName.JCR_MIXINTYPES;

    /**
     * Root node of the abstract query tree
     */
    private QueryRootNode root;

    /**
     * Session of the user executing this query
     */
    private SessionImpl session;

    /**
     * The shared item state manager of the workspace.
     */
    private ItemStateManager sharedItemMgr;

    /**
     * Namespace mappings to internal prefixes
     */
    private NamespaceMappings nsMappings;

    /**
     * The analyzer instance to use for contains function query parsing
     */
    private Analyzer analyzer;

    /**
     * The property type registry.
     */
    private PropertyTypeRegistry propRegistry;

    /**
     * Exceptions thrown during tree translation
     */
    private List exceptions = new ArrayList();

    /**
     * Creates a new <code>LuceneQueryBuilder</code> instance.
     *
     * @param root          the root node of the abstract query tree.
     * @param session       of the user executing this query.
     * @param sharedItemMgr the shared item state manager of the workspace.
     * @param nsMappings    namespace resolver for internal prefixes.
     * @param analyzer      for parsing the query statement of the contains function.
     * @param propReg       the property type registry.
     */
    private LuceneQueryBuilder(QueryRootNode root,
                               SessionImpl session,
                               ItemStateManager sharedItemMgr,
                               NamespaceMappings nsMappings,
                               Analyzer analyzer,
                               PropertyTypeRegistry propReg) {
        this.root = root;
        this.session = session;
        this.sharedItemMgr = sharedItemMgr;
        this.nsMappings = nsMappings;
        this.analyzer = analyzer;
        this.propRegistry = propReg;
    }

    /**
     * Creates a lucene {@link org.apache.lucene.search.Query} tree from an
     * abstract query tree.
     *
     * @param root          the root node of the abstract query tree.
     * @param session       of the user executing the query.
     * @param sharedItemMgr the shared item state manager of the workspace.
     * @param nsMappings    namespace resolver for internal prefixes.
     * @param analyzer      for parsing the query statement of the contains function.
     * @param propReg       the property type registry to lookup type information.
     * @return the lucene query tree.
     * @throws RepositoryException if an error occurs during the translation.
     */
    public static Query createQuery(QueryRootNode root,
                                    SessionImpl session,
                                    ItemStateManager sharedItemMgr,
                                    NamespaceMappings nsMappings,
                                    Analyzer analyzer,
                                    PropertyTypeRegistry propReg)
            throws RepositoryException {

        LuceneQueryBuilder builder = new LuceneQueryBuilder(root, session,
                sharedItemMgr, nsMappings, analyzer, propReg);

        Query q = builder.createLuceneQuery();
        if (builder.exceptions.size() > 0) {
            StringBuffer msg = new StringBuffer();
            for (Iterator it = builder.exceptions.iterator(); it.hasNext();) {
                msg.append(it.next().toString()).append('\n');
            }
            throw new RepositoryException("Exception building query: " + msg.toString());
        }
        return q;
    }

    /**
     * Starts the tree traversal and returns the lucene
     * {@link org.apache.lucene.search.Query}.
     *
     * @return the lucene <code>Query</code>.
     */
    private Query createLuceneQuery() {
        return (Query) root.accept(this, null);
    }

    //---------------------< QueryNodeVisitor interface >-----------------------

    public Object visit(QueryRootNode node, Object data) {
        BooleanQuery root = new BooleanQuery();

        Query wrapped = root;
        if (node.getLocationNode() != null) {
            wrapped = (Query) node.getLocationNode().accept(this, root);
        }

        return wrapped;
    }

    public Object visit(OrQueryNode node, Object data) {
        BooleanQuery orQuery = new BooleanQuery();
        Object[] result = node.acceptOperands(this, null);
        for (int i = 0; i < result.length; i++) {
            Query operand = (Query) result[i];
            orQuery.add(operand, false, false);
        }
        return orQuery;
    }

    public Object visit(AndQueryNode node, Object data) {
        Object[] result = node.acceptOperands(this, null);
        if (result.length == 0) {
            return null;
        }
        BooleanQuery andQuery = new BooleanQuery();
        for (int i = 0; i < result.length; i++) {
            Query operand = (Query) result[i];
            andQuery.add(operand, true, false);
        }
        return andQuery;
    }

    public Object visit(NotQueryNode node, Object data) {
        Object[] result = node.acceptOperands(this, null);
        if (result.length == 0) {
            return data;
        }
        // join the results
        BooleanQuery b = new BooleanQuery();
        for (int i = 0; i < result.length; i++) {
            b.add((Query) result[i], false, false);
        }
        // negate
        return new NotQuery(b);
    }

    public Object visit(ExactQueryNode node, Object data) {
        String field = "";
        String value = "";
        try {
            field = node.getPropertyName().toJCRName(nsMappings);
            value = node.getValue().toJCRName(nsMappings);
        } catch (NoPrefixDeclaredException e) {
            // will never happen, prefixes are created when unknown
        }
        return new TermQuery(new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, value)));
    }

    public Object visit(NodeTypeQueryNode node, Object data) {
        String field = "";
        List values = new ArrayList();
        try {
            values.add(node.getValue().toJCRName(nsMappings));
            NodeTypeManager ntMgr = session.getWorkspace().getNodeTypeManager();
            NodeType base = ntMgr.getNodeType(node.getValue().toJCRName(session.getNamespaceResolver()));
            if (base.isMixin()) {
                field = mixinTypes.toJCRName(nsMappings);
            } else {
                field = primaryType.toJCRName(nsMappings);
            }
            NodeTypeIterator allTypes = ntMgr.getAllNodeTypes();
            while (allTypes.hasNext()) {
                NodeType nt = allTypes.nextNodeType();
                NodeType[] superTypes = nt.getSupertypes();
                if (Arrays.asList(superTypes).contains(base)) {
                    values.add(nsMappings.translatePropertyName(nt.getName(),
                            session.getNamespaceResolver()));
                }
            }
        } catch (IllegalNameException e) {
            exceptions.add(e);
        } catch (UnknownPrefixException e) {
            exceptions.add(e);
        } catch (NoPrefixDeclaredException e) {
            // should never happen
            exceptions.add(e);
        } catch (RepositoryException e) {
            exceptions.add(e);
        }
        if (values.size() == 0) {
            // exception occured
            return new BooleanQuery();
        } else if (values.size() == 1) {
            Term t = new Term(FieldNames.PROPERTIES,
                    FieldNames.createNamedValue(field, (String) values.get(0)));
            return new TermQuery(t);
        } else {
            BooleanQuery b = new BooleanQuery();
            for (Iterator it = values.iterator(); it.hasNext();) {
                Term t = new Term(FieldNames.PROPERTIES,
                        FieldNames.createNamedValue(field, (String) it.next()));
                b.add(new TermQuery(t), false, false);
            }
            return b;
        }
    }

    public Object visit(TextsearchQueryNode node, Object data) {
        try {
            String fieldname;
            if (node.getPropertyName() == null) {
                // fulltext on node
                fieldname = FieldNames.FULLTEXT;
            } else {
                StringBuffer tmp = new StringBuffer();
                tmp.append(nsMappings.getPrefix(node.getPropertyName().getNamespaceURI()));
                tmp.append(":").append(FieldNames.FULLTEXT_PREFIX);
                tmp.append(node.getPropertyName().getLocalName());
                fieldname = tmp.toString();
            }
            QueryParser parser = new QueryParser(fieldname, analyzer);
            parser.setOperator(QueryParser.DEFAULT_OPERATOR_AND);
            // replace unescaped ' with " and escaped ' with just '
            StringBuffer query = new StringBuffer();
            String textsearch = node.getQuery();
            // the default lucene query parser recognizes 'AND' and 'NOT' as
            // keywords.
            textsearch = textsearch.replaceAll("AND", "and");
            textsearch = textsearch.replaceAll("NOT", "not");
            boolean escaped = false;
            for (int i = 0; i < textsearch.length(); i++) {
                if (textsearch.charAt(i) == '\\') {
                    if (escaped) {
                        query.append("\\\\");
                        escaped = false;
                    } else {
                        escaped = true;
                    }
                } else if (textsearch.charAt(i) == '\'') {
                    if (escaped) {
                        query.append('\'');
                        escaped = false;
                    } else {
                        query.append('\"');
                    }
                } else {
                    if (escaped) {
                        query.append('\\');
                        escaped = false;
                    }
                    query.append(textsearch.charAt(i));
                }
            }
            return parser.parse(query.toString());
        } catch (NamespaceException e) {
            exceptions.add(e);
        } catch (ParseException e) {
            exceptions.add(e);
        }
        return null;
    }

    public Object visit(PathQueryNode node, Object data) {
        Query context = null;
        LocationStepQueryNode[] steps = node.getPathSteps();
        if (steps.length > 0) {
            if (node.isAbsolute() && !steps[0].getIncludeDescendants()) {
                // eat up first step
                QName nameTest = steps[0].getNameTest();
                if (nameTest == null) {
                    // this is equivalent to the root node
                    context = new TermQuery(new Term(FieldNames.PARENT, ""));
                } else if (nameTest.getLocalName().length() == 0) {
                    // root node
                    context = new TermQuery(new Term(FieldNames.PARENT, ""));
                } else {
                    // then this is a node != the root node
                    // will never match anything!
                    String name = "";
                    try {
                        name = nameTest.toJCRName(nsMappings);
                    } catch (NoPrefixDeclaredException e) {
                        exceptions.add(e);
                    }
                    BooleanQuery and = new BooleanQuery();
                    and.add(new TermQuery(new Term(FieldNames.PARENT, "")), true, false);
                    and.add(new TermQuery(new Term(FieldNames.LABEL, name)), true, false);
                    context = and;
                }
                LocationStepQueryNode[] tmp = new LocationStepQueryNode[steps.length - 1];
                System.arraycopy(steps, 1, tmp, 0, steps.length - 1);
                steps = tmp;
            } else {
                // path is 1) relative or 2) descendant-or-self
                // use root node as context
                context = new TermQuery(new Term(FieldNames.PARENT, ""));
            }
        } else {
            exceptions.add(new InvalidQueryException("Number of location steps must be > 0"));
        }
        // loop over steps
        for (int i = 0; i < steps.length; i++) {
            context = (Query) steps[i].accept(this, context);
        }
        if (data instanceof BooleanQuery) {
            BooleanQuery constraint = (BooleanQuery) data;
            if (constraint.getClauses().length > 0) {
                constraint.add(context, true, false);
                context = constraint;
            }
        }
        return context;
    }

    public Object visit(LocationStepQueryNode node, Object data) {
        Query context = (Query) data;
        BooleanQuery andQuery = new BooleanQuery();

        if (context == null) {
            exceptions.add(new IllegalArgumentException("Unsupported query"));
        }

        // predicate on step?
        Object[] predicates = node.acceptOperands(this, data);
        for (int i = 0; i < predicates.length; i++) {
            andQuery.add((Query) predicates[i], true, false);
        }

        // check for position predicate
        QueryNode[] pred = node.getPredicates();
        for (int i = 0; i < pred.length; i++) {
            if (pred[i].getType() == QueryNode.TYPE_RELATION) {
                RelationQueryNode pos = (RelationQueryNode) pred[i];
                if (pos.getValueType() == QueryConstants.TYPE_POSITION) {
                    node.setIndex(pos.getPositionValue());
                }
            }
        }

        TermQuery nameTest = null;
        if (node.getNameTest() != null) {
            try {
                String internalName = node.getNameTest().toJCRName(nsMappings);
                nameTest = new TermQuery(new Term(FieldNames.LABEL, internalName));
            } catch (NoPrefixDeclaredException e) {
                // should never happen
                exceptions.add(e);
            }
        }

        if (node.getIncludeDescendants()) {
            if (nameTest != null) {
                andQuery.add(new DescendantSelfAxisQuery(context, nameTest), true, false);
            } else {
                // descendant-or-self with nametest=*
                if (predicates.length > 0) {
                    // if we have a predicate attached, the condition acts as
                    // the sub query.

                    // only use descendant axis if path is not //*
                    // otherwise the query for the predicate can be used itself
                    PathQueryNode pathNode = (PathQueryNode) node.getParent();
                    if (pathNode.getPathSteps()[0] != node) {
                        Query subQuery = new DescendantSelfAxisQuery(context, andQuery, false);
                        andQuery = new BooleanQuery();
                        andQuery.add(subQuery, true, false);
                    }
                } else {
                    // todo this will traverse the whole index, optimize!
                    Query subQuery = null;
                    try {
                        subQuery = new MatchAllQuery(primaryType.toJCRName(nsMappings));
                    } catch (NoPrefixDeclaredException e) {
                        // will never happen, prefixes are created when unknown
                    }
                    // only use descendant axis if path is not //*
                    PathQueryNode pathNode = (PathQueryNode) node.getParent();
                    if (pathNode.getPathSteps()[0] != node) {
                        context = new DescendantSelfAxisQuery(context, subQuery);
                        andQuery.add(new ChildAxisQuery(sharedItemMgr, context, null, node.getIndex()), true, false);
                    } else {
                        andQuery.add(subQuery, true, false);
                    }
                }
            }
        } else {
            // name test
            if (nameTest != null) {
                andQuery.add(new ChildAxisQuery(sharedItemMgr, context, nameTest.getTerm().text(), node.getIndex()), true, false);
            } else {
                // select child nodes
                andQuery.add(new ChildAxisQuery(sharedItemMgr, context, null, node.getIndex()), true, false);
            }
        }

        return andQuery;
    }

    public Object visit(DerefQueryNode node, Object data) {
        Query context = (Query) data;
        if (context == null) {
            exceptions.add(new IllegalArgumentException("Unsupported query"));
        }
        try {
            String refProperty = node.getRefProperty().toJCRName(nsMappings);
            String nameTest = null;
            if (node.getNameTest() != null) {
                nameTest = node.getNameTest().toJCRName(nsMappings);
            }
            return new DerefQuery(context, refProperty, nameTest);
        } catch (NoPrefixDeclaredException e) {
            // should never happen
            exceptions.add(e);
        }
        // fallback in case of exception
        return context;
    }

    public Object visit(RelationQueryNode node, Object data) {
        Query query;
        String[] stringValues = new String[1];
        switch (node.getValueType()) {
            case 0:
                // not set: either IS NULL or IS NOT NULL
                break;
            case QueryConstants.TYPE_DATE:
                stringValues[0] = DateField.dateToString(node.getDateValue());
                break;
            case QueryConstants.TYPE_DOUBLE:
                stringValues[0] = DoubleField.doubleToString(node.getDoubleValue());
                break;
            case QueryConstants.TYPE_LONG:
                stringValues[0] = LongField.longToString(node.getLongValue());
                break;
            case QueryConstants.TYPE_STRING:
                if (node.getOperation() == QueryConstants.OPERATION_EQ_GENERAL
                        || node.getOperation() == QueryConstants.OPERATION_EQ_VALUE
                        || node.getOperation() == QueryConstants.OPERATION_NE_GENERAL
                        || node.getOperation() == QueryConstants.OPERATION_NE_VALUE) {
                    // only use coercing on non-range operations
                    stringValues = getStringValues(node.getProperty(), node.getStringValue());
                } else {
                    stringValues[0] = node.getStringValue();
                }
                break;
            case QueryConstants.TYPE_POSITION:
                // ignore position. is handled in the location step
                return null;
            default:
                throw new IllegalArgumentException("Unknown relation type: "
                        + node.getValueType());
        }

        if (node.getProperty() == null) {
            exceptions.add(new InvalidQueryException("@* not supported in predicate"));
            return data;
        }

        String field = "";
        try {
            field = node.getProperty().toJCRName(nsMappings);
        } catch (NoPrefixDeclaredException e) {
            // should never happen
            exceptions.add(e);
        }

        switch (node.getOperation()) {
            case QueryConstants.OPERATION_EQ_VALUE:      // =
            case QueryConstants.OPERATION_EQ_GENERAL:
                BooleanQuery or = new BooleanQuery();
                for (int i = 0; i < stringValues.length; i++) {
                    or.add(new TermQuery(new Term(FieldNames.PROPERTIES,
                            FieldNames.createNamedValue(field, stringValues[i]))), false, false);
                }
                query = or;
                if (node.getOperation() == QueryConstants.OPERATION_EQ_VALUE) {
                    query = createSingleValueConstraint(or, field);
                }
                break;
            case QueryConstants.OPERATION_GE_VALUE:      // >=
            case QueryConstants.OPERATION_GE_GENERAL:
                or = new BooleanQuery();
                for (int i = 0; i < stringValues.length; i++) {
                    Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, "\uFFFF"));
                    or.add(new RangeQuery(lower, upper, true), false, false);
                }
                query = or;
                if (node.getOperation() == QueryConstants.OPERATION_GE_VALUE) {
                    query = createSingleValueConstraint(or, field);
                }
                break;
            case QueryConstants.OPERATION_GT_VALUE:      // >
            case QueryConstants.OPERATION_GT_GENERAL:
                or = new BooleanQuery();
                for (int i = 0; i < stringValues.length; i++) {
                    Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, "\uFFFF"));
                    or.add(new RangeQuery(lower, upper, false), false, false);
                }
                query = or;
                if (node.getOperation() == QueryConstants.OPERATION_GT_VALUE) {
                    query = createSingleValueConstraint(or, field);
                }
                break;
            case QueryConstants.OPERATION_LE_VALUE:      // <=
            case QueryConstants.OPERATION_LE_GENERAL:      // <=
                or = new BooleanQuery();
                for (int i = 0; i < stringValues.length; i++) {
                    Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, ""));
                    Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    or.add(new RangeQuery(lower, upper, true), false, false);
                }
                query = or;
                if (node.getOperation() == QueryConstants.OPERATION_LE_VALUE) {
                    query = createSingleValueConstraint(query, field);
                }
                break;
            case QueryConstants.OPERATION_LIKE:          // LIKE
                // the like operation always has one string value.
                // no coercing, see above
                if (stringValues[0].equals("%")) {
                    query = new MatchAllQuery(field);
                } else {
                    query = new WildcardQuery(FieldNames.PROPERTIES, field, stringValues[0]);
                }
                break;
            case QueryConstants.OPERATION_LT_VALUE:      // <
            case QueryConstants.OPERATION_LT_GENERAL:
                or = new BooleanQuery();
                for (int i = 0; i < stringValues.length; i++) {
                    Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, ""));
                    Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    or.add(new RangeQuery(lower, upper, false), false, false);
                }
                query = or;
                if (node.getOperation() == QueryConstants.OPERATION_LT_VALUE) {
                    query = createSingleValueConstraint(or, field);
                }
                break;
            case QueryConstants.OPERATION_NE_VALUE:      // !=
                // match nodes with property 'field' that includes svp and mvp
                BooleanQuery notQuery = new BooleanQuery();
                notQuery.add(new MatchAllQuery(field), false, false);
                // exclude all nodes where 'field' has the term in question
                for (int i = 0; i < stringValues.length; i++) {
                    Term t = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    notQuery.add(new TermQuery(t), false, true);
                }
                // and exclude all nodes where 'field' is multi valued
                notQuery.add(new TermQuery(new Term(FieldNames.MVP, field)), false, true);
                query = notQuery;
                break;
            case QueryConstants.OPERATION_NE_GENERAL:    // !=
                // that's:
                // all nodes with property 'field'
                // minus the nodes that have a single property 'field' that is
                //    not equal to term in question
                // minus the nodes that have a multi-valued property 'field' and
                //    all values are equal to term in question
                notQuery = new BooleanQuery();
                notQuery.add(new MatchAllQuery(field), false, false);
                for (int i = 0; i < stringValues.length; i++) {
                    // exclude the nodes that have the term and are single valued
                    Term t = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                    Query svp = new NotQuery(new TermQuery(new Term(FieldNames.MVP, field)));
                    BooleanQuery and = new BooleanQuery();
                    and.add(new TermQuery(t), true, false);
                    and.add(svp, true, false);
                    notQuery.add(and, false, true);
                }
                // todo above also excludes multi-valued properties that contain
                //      multiple instances of only stringValues. e.g. text={foo, foo}
                query = notQuery;
                break;
            case QueryConstants.OPERATION_NULL:
                query = new NotQuery(new MatchAllQuery(field));
                break;
            case QueryConstants.OPERATION_NOT_NULL:
                query = new MatchAllQuery(field);
                break;
            default:
                throw new IllegalArgumentException("Unknown relation operation: "
                        + node.getOperation());
        }
        return query;
    }

    public Object visit(OrderQueryNode node, Object data) {
        return data;
    }

    //---------------------------< internal >-----------------------------------

    /**
     * Wraps a constraint query around <code>q</code> that limits the nodes to
     * those where <code>propName</code> is the name of a single value property
     * on the node instance.
     *
     * @param q        the query to wrap.
     * @param propName the name of a property that only has one value.
     * @return the wrapped query <code>q</code>.
     */
    private Query createSingleValueConstraint(Query q, String propName) {
        // get nodes with multi-values in propName
        Query mvp = new TermQuery(new Term(FieldNames.MVP, propName));
        // now negate, that gives the nodes that have propName as single
        // values but also all others
        Query svp = new NotQuery(mvp);
        // now join the two, which will result in those nodes where propName
        // only contains a single value. This works because q already restricts
        // the result to those nodes that have a property propName
        BooleanQuery and = new BooleanQuery();
        and.add(q, true, false);
        and.add(svp, true, false);
        return and;
    }

    /**
     * Returns an array of String values to be used as a term to lookup the search index
     * for a String <code>literal</code> of a certain property name. This method
     * will lookup the <code>propertyName</code> in the node type registry
     * trying to find out the {@link javax.jcr.PropertyType}s.
     * If no property type is found looking up node type information, this
     * method will guess the property type.
     *
     * @param propertyName the name of the property in the relation.
     * @param literal      the String literal in the relation.
     * @return the String values to use as term for the query.
     */
    private String[] getStringValues(QName propertyName, String literal) {
        PropertyTypeRegistry.TypeMapping[] types = propRegistry.getPropertyTypes(propertyName);
        List values = new ArrayList();
        for (int i = 0; i < types.length; i++) {
            switch (types[i].type) {
                case PropertyType.NAME:
                    // try to translate name
                    try {
                        values.add(nsMappings.translatePropertyName(literal, session.getNamespaceResolver()));
                        log.debug("Coerced " + literal + " into NAME.");
                    } catch (IllegalNameException e) {
                        log.warn("Unable to coerce '" + literal + "' into a NAME: " + e.toString());
                    } catch (UnknownPrefixException e) {
                        log.warn("Unable to coerce '" + literal + "' into a NAME: " + e.toString());
                    }
                    break;
                case PropertyType.PATH:
                    // try to translate path
                    try {
                        Path p = Path.create(literal, session.getNamespaceResolver(), false);
                        values.add(p.toJCRPath(nsMappings));
                        log.debug("Coerced " + literal + " into PATH.");
                    } catch (MalformedPathException e) {
                        log.warn("Unable to coerce '" + literal + "' into a PATH: " + e.toString());
                    } catch (NoPrefixDeclaredException e) {
                        log.warn("Unable to coerce '" + literal + "' into a PATH: " + e.toString());
                    }
                    break;
                case PropertyType.DATE:
                    // try to parse date
                    Calendar c = ISO8601.parse(literal);
                    if (c != null) {
                        values.add(DateField.timeToString(c.getTimeInMillis()));
                        log.debug("Coerced " + literal + " into DATE.");
                    } else {
                        log.warn("Unable to coerce '" + literal + "' into a DATE.");
                    }
                    break;
                case PropertyType.DOUBLE:
                    // try to parse double
                    try {
                        double d = Double.parseDouble(literal);
                        values.add(DoubleField.doubleToString(d));
                        log.debug("Coerced " + literal + " into DOUBLE.");
                    } catch (NumberFormatException e) {
                        log.warn("Unable to coerce '" + literal + "' into a DOUBLE: " + e.toString());
                    }
                    break;
                case PropertyType.LONG:
                    // try to parse long
                    try {
                        long l = Long.parseLong(literal);
                        values.add(LongField.longToString(l));
                        log.debug("Coerced " + literal + " into LONG.");
                    } catch (NumberFormatException e) {
                        log.warn("Unable to coerce '" + literal + "' into a LONG: " + e.toString());
                    }
                    break;
                case PropertyType.STRING:
                    values.add(literal);
                    log.debug("Using literal " + literal + " as is.");
                    break;
            }
        }
        if (values.size() == 0) {
            // use literal as is then try to guess other types
            values.add(literal);

            // try to guess property type
            if (literal.indexOf('/') > -1) {
                // might be a path
                try {
                    values.add(Path.create(literal, session.getNamespaceResolver(), false).toJCRPath(nsMappings));
                    log.debug("Coerced " + literal + " into PATH.");
                } catch (Exception e) {
                    // not a path
                }
            }
            if (XMLChar.isValidName(literal)) {
                // might be a name
                try {
                    values.add(nsMappings.translatePropertyName(literal, session.getNamespaceResolver()));
                    log.debug("Coerced " + literal + " into NAME.");
                } catch (Exception e) {
                    // not a name
                }
            }
            if (literal.indexOf(':') > -1) {
                // is it a date?
                Calendar c = ISO8601.parse(literal);
                if (c != null) {
                    values.add(DateField.timeToString(c.getTimeInMillis()));
                    log.debug("Coerced " + literal + " into DATE.");
                }
            } else {
                // long or double are possible at this point
                try {
                    values.add(LongField.longToString(Long.parseLong(literal)));
                    log.debug("Coerced " + literal + " into LONG.");
                } catch (NumberFormatException e) {
                    // not a long
                    // try double
                    try {
                        values.add(DoubleField.doubleToString(Double.parseDouble(literal)));
                        log.debug("Coerced " + literal + " into DOUBLE.");
                    } catch (NumberFormatException e1) {
                        // not a double
                    }
                }
            }
        }
        // if still no values use literal as is
        if (values.size() == 0) {
            values.add(literal);
            log.debug("Using literal " + literal + " as is.");
        }
        return (String[]) values.toArray(new String[values.size()]);
    }
}
