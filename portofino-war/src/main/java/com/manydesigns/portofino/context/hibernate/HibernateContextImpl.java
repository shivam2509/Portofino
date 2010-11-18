/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.portofino.context.hibernate;

import com.manydesigns.elements.fields.search.Criteria;
import com.manydesigns.elements.fields.search.Criterion;
import com.manydesigns.elements.fields.search.TextMatchMode;
import com.manydesigns.elements.logging.LogUtil;
import com.manydesigns.elements.reflection.ClassAccessor;
import com.manydesigns.elements.reflection.PropertyAccessor;
import com.manydesigns.elements.text.OgnlSqlFormat;
import com.manydesigns.elements.text.QueryStringWithParameters;
import com.manydesigns.elements.util.ReflectionUtil;
import com.manydesigns.portofino.context.Context;
import com.manydesigns.portofino.database.ConnectionProvider;
import com.manydesigns.portofino.database.platforms.DatabasePlatform;
import com.manydesigns.portofino.model.Model;
import com.manydesigns.portofino.model.datamodel.*;
import com.manydesigns.portofino.model.diff.DatabaseDiff;
import com.manydesigns.portofino.model.diff.DiffUtil;
import com.manydesigns.portofino.model.diff.MergeDiffer;
import com.manydesigns.portofino.model.io.ConnectionsParser;
import com.manydesigns.portofino.model.io.ModelParser;
import com.manydesigns.portofino.model.io.ModelWriter;
import com.manydesigns.portofino.model.site.SiteNode;
import com.manydesigns.portofino.model.site.usecases.UseCase;
import com.manydesigns.portofino.reflection.TableAccessor;
import com.manydesigns.portofino.reflection.UseCaseAccessor;
import com.manydesigns.portofino.system.model.users.User;
import org.apache.commons.lang.time.StopWatch;
import org.hibernate.*;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Restrictions;
import org.hibernate.dialect.Dialect;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.tool.hbm2ddl.DatabaseMetadata;

import java.io.File;
import java.io.Serializable;
import java.sql.Connection;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class HibernateContextImpl implements Context {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    protected static final String WHERE_STRING = " WHERE ";
    protected static final Pattern FROM_PATTERN =
            Pattern.compile("[fF][rR][oO][mM]\\s+(\\S+\\.\\S+\\.\\S+).*");

    protected static final Pattern SELECT_PATTERN =
            Pattern.compile("[sS][eE][lL][eE][cC][tT]\\s+(\\S+\\.\\S+\\.\\S+).*");


    //**************************************************************************
    // Fields
    //**************************************************************************

    protected List<ConnectionProvider> connectionProviders;
    protected Model model;
    protected Map<String, HibernateDatabaseSetup> setups;
    protected final ThreadLocal<StopWatch> stopWatches;
    protected final ThreadLocal<User> threadUsers;
    protected final List<SiteNode> siteNodes;
    protected File xmlModelFile;

    public static final Logger logger =
            LogUtil.getLogger(HibernateContextImpl.class);
    private static final String PORTOFINO_PUBLIC_USERS = "portofino.public.users";

    //**************************************************************************
    // Constructors
    //**************************************************************************

    public HibernateContextImpl() {
        stopWatches = new ThreadLocal<StopWatch>();
        siteNodes = new ArrayList<SiteNode>();
        threadUsers = new ThreadLocal<User>();
    }

    //**************************************************************************
    // Model loading
    //**************************************************************************

    public void loadConnections(File file) {
        LogUtil.infoMF(logger, "Loading connections from file: {0}",
                file.getAbsolutePath());

        ConnectionsParser parser = new ConnectionsParser();
        try {
            connectionProviders = parser.parse(file);
            for (ConnectionProvider current : connectionProviders) {
                current.test();
            }
        } catch (Exception e) {
            LogUtil.severeMF(logger, "Cannot load/parse file: {0}", e, file);
        }

        LogUtil.exiting(logger, "loadConnectionsAsResource");
    }

    public void loadXmlModel(File file) {
        LogUtil.infoMF(logger, "Loading xml model from file: {0}",
                file.getAbsolutePath());

        ModelParser parser = new ModelParser();
        try {
            Model loadedModel = parser.parse(file);
            installDataModel(loadedModel);
            xmlModelFile = file;
        } catch (Exception e) {
            LogUtil.severeMF(logger, "Cannot load/parse model: {0}", e, file);
        }
    }

    public void saveXmlModel() {
        ModelWriter modelWriter = new ModelWriter();
        try {
            modelWriter.write(model, xmlModelFile);
            LogUtil.infoMF(logger,
                    "Saved xml model to file: {0}", xmlModelFile);
        } catch (Throwable e) {
            LogUtil.severeMF(logger,
                    "Cannot save xml model to file: {0}", e, xmlModelFile);
        }
    }

    private synchronized void installDataModel(Model newModel) {
        try {
            HashMap<String, HibernateDatabaseSetup> newSetups =
                    new HashMap<String, HibernateDatabaseSetup>();
            for (Database database : newModel.getDatabases()) {
                String databaseName = database.getDatabaseName();

                ConnectionProvider connectionProvider =
                        getConnectionProvider(databaseName);
                if (connectionProvider.getStatus()
                        .equals(ConnectionProvider.STATUS_CONNECTED)) {
                    HibernateConfig builder =
                            new HibernateConfig(connectionProvider);
                    Configuration configuration =
                            builder.buildSessionFactory(database);
                    SessionFactoryImpl sessionFactory =
                            (SessionFactoryImpl) configuration
                                    .buildSessionFactory();

                    HibernateDatabaseSetup setup =
                            new HibernateDatabaseSetup(
                                    configuration, sessionFactory);
                    newSetups.put(databaseName, setup);
                }
            }
            setups = newSetups;
            model = newModel;
        } catch (Exception e) {
            LogUtil.severe(logger, "Cannot install model", e);
        }
    }

    //**************************************************************************
    // Database stuff
    //**************************************************************************

    public ConnectionProvider getConnectionProvider(String databaseName) {
        for (ConnectionProvider current : connectionProviders) {
            if (current.getDatabaseName().equals(databaseName)) {
                return current;
            }
        }
        return null;
    }

    //**************************************************************************
    // Modell access
    //**************************************************************************

    public List<ConnectionProvider> getConnectionProviders() {
        return connectionProviders;
    }

    public Model getModel() {
        return model;
    }

    public void syncDataModel() {
        MergeDiffer mergeDiffer = new MergeDiffer();

        for (ConnectionProvider current : connectionProviders) {
            Database sourceDatabase = current.readModel();

            Database targetDatabase =
                    model.findDatabaseByName(current.getDatabaseName());

            DatabaseDiff diff =
                    DiffUtil.diff(sourceDatabase, targetDatabase);

            mergeDiffer.diffDatabase(diff);
        }

        model.init();
        saveXmlModel();
        installDataModel(model);
        saveXmlModel();
    }

    //**************************************************************************
    // Persistance
    //**************************************************************************

    public Object getObjectByPk(String qualifiedTableName,
                                Serializable pk) {
        Session session = getSession(qualifiedTableName);
        TableAccessor table = getTableAccessor(qualifiedTableName);
        Object result = null;
        PropertyAccessor[] keyProperties = table.getKeyProperties();
        int size = keyProperties.length;
        if (size > 1) {
            startTimer();
            result = session.load(qualifiedTableName, pk);
            stopTimer();
            return result;
        }
        startTimer();
        PropertyAccessor propertyAccessor = keyProperties[0];
        try {
            Serializable key = (Serializable) propertyAccessor.get(pk);
            result = session.load(qualifiedTableName, key);
        } catch (Throwable e) {
            LogUtil.warningMF(logger,
                    "Cannot invoke property accessor for {0} on class {1}",
                    e, propertyAccessor.getName(), table.getName());
        }
        stopTimer();
        return result;
    }


    public List<Object> getAllObjects(String qualifiedTableName) {
        Session session = getSession(qualifiedTableName);

        org.hibernate.Criteria hibernateCriteria;
        Table table = model.findTableByQualifiedName(qualifiedTableName);

        if (table.getJavaClass() == null) {
            hibernateCriteria = session.createCriteria(qualifiedTableName);
        } else {
            hibernateCriteria = session.createCriteria
                    (ReflectionUtil.loadClass(table.getJavaClass()));
        }

        startTimer();
        //noinspection unchecked
        List<Object> result = hibernateCriteria.list();
        stopTimer();
        return result;
    }

    protected Session getSession(String qualifiedTableName) {
        Table table = model.findTableByQualifiedName(qualifiedTableName);
        String databaseName = table.getDatabaseName();
        return setups.get(databaseName).getThreadSession();
    }



    public QueryStringWithParameters getQueryStringWithParametersForCriteria(
            Criteria criteria) {
        if (criteria == null) {
            return new QueryStringWithParameters("", new Object[0]);
        }
        ClassAccessor classAccessor = criteria.getClassAccessor();
        String qualifiedTableName = classAccessor.getName();

        ArrayList<Object> parametersList = new ArrayList<Object>();
        StringBuilder whereBuilder = new StringBuilder();
        for (Criterion criterion : criteria) {
            PropertyAccessor accessor = criterion.getPropertyAccessor();
            String hqlFormat;
            if (criterion instanceof Criteria.EqCriterion) {
                Criteria.EqCriterion eqCriterion =
                        (Criteria.EqCriterion) criterion;
                Object value = eqCriterion.getValue();
                hqlFormat = "{0} = ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.NeCriterion) {
                Criteria.NeCriterion neCriterion =
                        (Criteria.NeCriterion) criterion;
                Object value = neCriterion.getValue();
                hqlFormat = "{0} <> ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.BetweenCriterion) {
                Criteria.BetweenCriterion betweenCriterion =
                        (Criteria.BetweenCriterion) criterion;
                Object min = betweenCriterion.getMin();
                Object max = betweenCriterion.getMax();
                hqlFormat = "{0} >= ? AND {0} <= ?";
                parametersList.add(min);
                parametersList.add(max);
            } else if (criterion instanceof Criteria.GtCriterion) {
                Criteria.GtCriterion gtCriterion =
                        (Criteria.GtCriterion) criterion;
                Object value = gtCriterion.getValue();
                hqlFormat = "{0} > ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.GeCriterion) {
                Criteria.GeCriterion gtCriterion =
                        (Criteria.GeCriterion) criterion;
                Object value = gtCriterion.getValue();
                hqlFormat = "{0} >= ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.LtCriterion) {
                Criteria.LtCriterion ltCriterion =
                        (Criteria.LtCriterion) criterion;
                Object value = ltCriterion.getValue();
                hqlFormat = "{0} < ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.LeCriterion) {
                Criteria.LeCriterion leCriterion =
                        (Criteria.LeCriterion) criterion;
                Object value = leCriterion.getValue();
                hqlFormat = "{0} <= ?";
                parametersList.add(value);
            } else if (criterion instanceof Criteria.LikeCriterion) {
                Criteria.LikeCriterion likeCriterion =
                        (Criteria.LikeCriterion) criterion;
                String value = (String) likeCriterion.getValue();
                String pattern = processTextMatchMode(
                        likeCriterion.getTextMatchMode(), value);
                hqlFormat = "{0} like ?";
                parametersList.add(pattern);
            } else if (criterion instanceof Criteria.IlikeCriterion) {
                Criteria.IlikeCriterion ilikeCriterion =
                        (Criteria.IlikeCriterion) criterion;
                String value = (String) ilikeCriterion.getValue();
                String pattern = processTextMatchMode(
                        ilikeCriterion.getTextMatchMode(), value);
                hqlFormat = "lower({0}) like lower(?)";
                parametersList.add(pattern);
            } else if (criterion instanceof Criteria.IsNullCriterion) {
                hqlFormat = "{0} is null";
            } else if (criterion instanceof Criteria.IsNotNullCriterion) {
                hqlFormat = "{0} is not null";
            } else {
                LogUtil.severeMF(logger, "Unrecognized criterion: ", criterion);
                throw new InternalError("Unrecognied criterion");
            }

            String hql = MessageFormat.format(hqlFormat,
                    accessor.getName());

            if (whereBuilder.length() > 0) {
                whereBuilder.append(" AND ");
            }
            whereBuilder.append(hql);
        }
        String whereClause = whereBuilder.toString();
        String queryString;
        if (whereClause.length() > 0) {
            queryString = MessageFormat.format(
                    "FROM {0}" + WHERE_STRING + "{1}",
                    qualifiedTableName,
                    whereClause);
        } else {
            queryString = MessageFormat.format(
                    "FROM {0}",
                    qualifiedTableName);
        }

        Object[] parameters = new Object[parametersList.size()];
        parametersList.toArray(parameters);

        return new QueryStringWithParameters(queryString, parameters);
    }

    protected String processTextMatchMode(TextMatchMode textMatchMode,
                                          String value) {
        String pattern;
        switch (textMatchMode) {
            case EQUALS:
                pattern = value;
                break;
            case CONTAINS:
                pattern = "%" + value + "%";
                break;
            case STARTS_WITH:
                pattern = value + "%";
                break;
            case ENDS_WITH:
                pattern = "%" + value;
                break;
            default:
                String msg = MessageFormat.format(
                        "Unrecognized text match mode: {0}",
                        textMatchMode);
                logger.severe(msg);
                throw new InternalError(msg);
        }
        return pattern;
    }

    public List<Object> getObjects(Criteria criteria) {
        QueryStringWithParameters queryStringWithParameters =
                getQueryStringWithParametersForCriteria(criteria);

        return runHqlQuery(
                queryStringWithParameters.getQueryString(),
                queryStringWithParameters.getParamaters());
    }


    public List<Object> getObjects(String queryString, Object rootObject) {
        OgnlSqlFormat sqlFormat = OgnlSqlFormat.create(queryString);
        String formatString = sqlFormat.getFormatString();
        Object[] parameters = sqlFormat.evaluateOgnlExpressions(rootObject);

        return runHqlQuery(formatString, parameters);
    }

    public List<Object> getObjects(String qualifiedTableName, String queryString, Object rootObject) {
        OgnlSqlFormat sqlFormat = OgnlSqlFormat.create(queryString);
        String formatString = sqlFormat.getFormatString();
        Object[] parameters = sqlFormat.evaluateOgnlExpressions(rootObject);

        return runHqlQuery(qualifiedTableName, formatString, parameters);
    }

    public List<Object> getObjects(String queryString) {
        return getObjects(queryString, null);
    }

    public String getQualifiedTableNameFromQueryString(String queryString) {
        Matcher matcher = FROM_PATTERN.matcher(queryString);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public List<Object> getObjects(String queryString, Criteria criteria) {
        return getObjects(queryString, criteria, null);
    }

    public List<Object> getObjects(String queryString,
                                   Criteria criteria,
                                   Object rootObject) {
        OgnlSqlFormat sqlFormat = OgnlSqlFormat.create(queryString);
        String formatString = sqlFormat.getFormatString();
        Object[] parameters = sqlFormat.evaluateOgnlExpressions(rootObject);
        boolean formatStringContainsWhere = formatString.toUpperCase().contains(WHERE_STRING);

        QueryStringWithParameters criteriaQuery =
                getQueryStringWithParametersForCriteria(criteria);
        String criteriaQueryString = criteriaQuery.getQueryString();
        Object[] criteriaParameters = criteriaQuery.getParamaters();

        // merge the hql strings
        int whereIndex = criteriaQueryString.toUpperCase().indexOf(WHERE_STRING);
        String criteriaWhereClause;
        if (whereIndex >= 0) {
            criteriaWhereClause =
                    criteriaQueryString.substring(
                            whereIndex + WHERE_STRING.length());
        } else {
            criteriaWhereClause = "";
        }

        String fullQueryString;
        if (criteriaWhereClause.length() > 0) {
            if (formatStringContainsWhere) {
                fullQueryString = MessageFormat.format(
                        "{0} AND {1}",
                        formatString,
                        criteriaWhereClause);
            } else {
                fullQueryString = MessageFormat.format(
                        "{0} WHERE {1}",
                        formatString,
                        criteriaWhereClause);
            }
        } else {
            fullQueryString = formatString;
        }

        // merge the parameters
        ArrayList<Object> mergedParametersList = new ArrayList<Object>();
        mergedParametersList.addAll(Arrays.asList(parameters));
        mergedParametersList.addAll(Arrays.asList(criteriaParameters));
        Object[] mergedParameters = new Object[mergedParametersList.size()];
        mergedParametersList.toArray(mergedParameters);

        return runHqlQuery(fullQueryString, mergedParameters);
    }

    private List<Object> runHqlQuery(String queryString, Object[] parameters) {
        String qualifiedTableName =
                getQualifiedTableNameFromQueryString(queryString);
        Session session = getSession(qualifiedTableName);

        Query query = session.createQuery(queryString);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i, parameters[i]);
        }

        startTimer();
        //noinspection unchecked
        List<Object> result = query.list();
        stopTimer();
        return result;
    }

    private List<Object> runHqlQuery(String qualifiedTableName, String queryString, Object[] parameters) {
        Session session = getSession(qualifiedTableName);

        Query query = session.createQuery(queryString);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i, parameters[i]);
        }

        startTimer();
        //noinspection unchecked
        List<Object> result = query.list();
        stopTimer();
        return result;
    }


    public void saveObject(String qualifiedTableName, Object obj) {
        Session session = getSession(qualifiedTableName);
        session.beginTransaction();

        try {
            startTimer();
            session.save(qualifiedTableName, obj);
            //session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            stopTimer();
        }
    }


    public void updateObject(String qualifiedTableName, Object obj) {
        Session session = getSession(qualifiedTableName);
        session.beginTransaction();
        try {
            startTimer();
            session.update(qualifiedTableName, obj);
            //session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            stopTimer();
        }
    }

    public void deleteObject(String qualifiedTableName, Object obj) {
        Session session = getSession(qualifiedTableName);
        session.beginTransaction();
        try {
            Object obj2 = getObjectByPk(qualifiedTableName, (Serializable) obj);
            startTimer();
            session.delete(qualifiedTableName, obj2);
            //session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            stopTimer();
        }
    }

    public List<Object[]> runSql(String databaseName, String sql) {
        Session session = setups.get(databaseName).getThreadSession();
        OgnlSqlFormat sqlFormat = OgnlSqlFormat.create(sql);
        String formatString = sqlFormat.getFormatString();
        Object[] parameters = sqlFormat.evaluateOgnlExpressions(null);

        SQLQuery query = session.createSQLQuery(formatString);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i, parameters[i]);
        }

        startTimer();
        //noinspection unchecked
        List<Object[]> result = query.list();
        stopTimer();

        return result;
    }

    public void openSession() {
        for (HibernateDatabaseSetup current : setups.values()) {
            SessionFactory sessionFactory = current.getSessionFactory();
            Session session = sessionFactory.openSession();
            current.setThreadSession(session);
        }
    }


    public void closeSession() {
        for (HibernateDatabaseSetup current : setups.values()) {
            Session session = current.getThreadSession();
            if (session != null) {
                try {
                    session.close();
                } catch (Throwable e) {
                    LogUtil.warning(logger,
                            "Exception while closing Hibernate session", e);
                }
            }
            current.setThreadSession(null);
        }
    }

    public void commit(String databaseName) {
        Session session = setups.get(databaseName).getThreadSession();
        try {
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            throw e;
        }
    }

    public void rollback(String databaseName) {
        Session session = setups.get(databaseName).getThreadSession();
        session.getTransaction().rollback();
    }
    
   public void commit() {
        for (HibernateDatabaseSetup current : setups.values()) {
            Session session = current.getThreadSession();
            if (session != null) {
                Transaction tx = session.getTransaction();
                if (null != tx && tx.isActive()) {
                    try {
                        tx.commit();
                    } catch (HibernateException e) {
                        tx.rollback();
                        throw e;
                    }
                }
            }
        }
    }

    public void rollback() {
        for (HibernateDatabaseSetup current : setups.values()) {
            Session session = current.getThreadSession();
            if (session != null) {
                Transaction tx = session.getTransaction();
                if (null != tx && tx.isActive()) {
                    tx.rollback();
                }
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    public List<Object> getRelatedObjects(String qualifiedTableName,
                                          Object obj,
                                          String oneToManyRelationshipName) {
        ForeignKey relationship =
                model.findOneToManyRelationship(
                        qualifiedTableName, oneToManyRelationshipName);
        Table toTable = relationship.getActualToTable();
        Table fromTable = relationship.getFromTable();
        //Session session = getSession(qualifiedTableName);
        Session session = getSession(fromTable.getQualifiedName());

        ClassAccessor toAccessor = getTableAccessor(qualifiedTableName);

        boolean sameDB = toTable.getDatabaseName().equals(fromTable.getDatabaseName());
        try {
            org.hibernate.Criteria criteria =
                    session.createCriteria(fromTable.getQualifiedName());
            for (Reference reference : relationship.getReferences()) {
                Column fromColumn = reference.getActualFromColumn();
                Column toColumn = reference.getActualToColumn();
                PropertyAccessor toPropertyAccessor
                        = toAccessor.getProperty(toColumn.getActualPropertyName());
                Object toValue = toPropertyAccessor.get(obj);
                criteria.add(Restrictions.eq(fromColumn.getActualPropertyName(),
                        toValue));
            }
            startTimer();
            //noinspection unchecked
            List<Object> result = criteria.list();
            stopTimer();
            return result;
        } catch (Throwable e) {
            LogUtil.warningMF(logger,
                    "Cannot access relationship {0} on table {1}",
                    e, oneToManyRelationshipName, qualifiedTableName);
        }
        return null;
    }

    //**************************************************************************
    // DDL
    //**************************************************************************

    public List<String> getDDLCreate() {
        List<String> result = new ArrayList<String>();
        for (Database db : model.getDatabases()) {
            result.add("-- DB: " + db.getDatabaseName());
            HibernateDatabaseSetup setup = setups.get(db.getDatabaseName());
            ConnectionProvider connectionProvider =
                    getConnectionProvider(db.getDatabaseName());
            DatabasePlatform platform = connectionProvider.getDatabasePlatform();
            Dialect dialect = platform.getHibernateDialect();
            Configuration conf = setup.getConfiguration();
            String[] ddls = conf.generateSchemaCreationScript(dialect);
            result.addAll(Arrays.asList(ddls));
        }
        return result;
    }

    public List<String> getDDLUpdate() {
        List<String> result = new ArrayList<String>();


        for (Database db : model.getDatabases()) {
            HibernateDatabaseSetup setup = setups.get(db.getDatabaseName());
            DatabaseMetadata databaseMetadata;
            ConnectionProvider provider =
                    getConnectionProvider(db.getDatabaseName());
            DatabasePlatform platform = provider.getDatabasePlatform();
            Dialect dialect = platform.getHibernateDialect();
            Connection conn = null;
            try {
                conn = provider.acquireConnection();

                databaseMetadata = new DatabaseMetadata(conn, dialect);

                result.add("-- DB: " + db.getDatabaseName());

                Configuration conf = setup.getConfiguration();
                String[] ddls = conf.generateSchemaUpdateScript(
                        dialect, databaseMetadata);
                result.addAll(Arrays.asList(ddls));

            } catch (Throwable e) {
                LogUtil.warningMF(logger,
                        "Cannot retrieve DDLs for update DB for DB: {0}",
                        e, db.getDatabaseName());
            } finally {
                provider.releaseConnection(conn);
            }

        }
        return result;
    }

    public TableAccessor getTableAccessor(String qualifiedTableName) {
        Table table = model.findTableByQualifiedName(qualifiedTableName);
        return new TableAccessor(table);
    }

    public UseCaseAccessor getUseCaseAccessor(UseCase useCase) {
        String qualifiedTableName = useCase.getTable();
        TableAccessor tableAccessor = getTableAccessor(qualifiedTableName);
        return new UseCaseAccessor(useCase, tableAccessor);
    }

    //**************************************************************************
    // User
    //**************************************************************************
    public User login(String email, String password) {
        String qualifiedTableName = PORTOFINO_PUBLIC_USERS;
        Session session = getSession(qualifiedTableName);
        org.hibernate.Criteria criteria = session.createCriteria(qualifiedTableName);
        criteria.add(Restrictions.eq("email", email));
        criteria.add(Restrictions.eq("pwd", password));
        startTimer();

        @SuppressWarnings({"unchecked"})
        List<Object> result = (List<Object>) criteria.list();
        stopTimer();

        if (result.size() == 1) {
            User authUser = (User) result.get(0);
            return authUser;
        } else {
            return null;
        }
    }

    public User findUserByEmail(String email) {
        String qualifiedTableName = PORTOFINO_PUBLIC_USERS;
        Session session = getSession(qualifiedTableName);
        org.hibernate.Criteria criteria = session.createCriteria(qualifiedTableName);
        criteria.add(Restrictions.eq("email", email));
        startTimer();
        @SuppressWarnings({"unchecked"})
        List<Object> result = (List<Object>) criteria.list();
        stopTimer();

        if (result.size() == 1) {
            User user = (User) result.get(0);
            return user;
        } else {
            return null;
        }
    }

    public User findUserByToken(String token) {
        String qualifiedTableName = PORTOFINO_PUBLIC_USERS;
        Session session = getSession(qualifiedTableName);
        org.hibernate.Criteria criteria = session.createCriteria(qualifiedTableName);
        criteria.add(Restrictions.eq("token", token));
        startTimer();
        @SuppressWarnings({"unchecked"})
        List<Object> result = (List<Object>) criteria.list();
        stopTimer();

        if (result.size() == 1) {
            User user = (User) result.get(0);
            return user;
        } else {
            return null;
        }
    }

    public void logout() {
       setCurrentUser(null);
    }

    public User getCurrentUser() {
        return threadUsers.get();
    }

    public void setCurrentUser(User user) {
        threadUsers.set(user);
    }


    //**************************************************************************
    // Timers
    //**************************************************************************

    public void resetDbTimer() {
        stopWatches.set(null);
    }

    public long getDbTime() {
        StopWatch stopWatch = stopWatches.get();
        if (stopWatch != null) {
            return stopWatch.getTime();
        }
        return 0L;
    }

    private void startTimer() {
        StopWatch stopWatch = stopWatches.get();
        if (stopWatch == null) {
            stopWatch = new StopWatch();
            stopWatches.set(stopWatch);
            stopWatch.start();
        } else {
            stopWatch.resume();
        }
    }

    private void stopTimer() {
        StopWatch stopWatch = stopWatches.get();
        if (stopWatch != null) {
            stopWatch.suspend();
        }
    }
}
