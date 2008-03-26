package org.jboss.seam.persistence;
import static org.jboss.seam.ScopeType.STATELESS;
import static org.jboss.seam.annotations.Install.FRAMEWORK;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.transaction.Synchronization;

import org.hibernate.EntityMode;
import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.StaleStateException;
import org.hibernate.TransientObjectException;
import org.hibernate.ejb.event.Callback;
import org.hibernate.ejb.event.EJB3PostLoadEventListener;
import org.hibernate.ejb.event.EntityCallbackHandler;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.event.PostLoadEventListener;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.VersionType;
import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.Seam;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.core.Expressions.ValueExpression;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
/**
 * Support for non-standardized features of Hibernate, when
 * used as the JPA persistence provider.
 * 
 * @author Gavin King
 * @author Pete Muir
 *
 */
@Name("org.jboss.seam.persistence.hibernatePersistenceProvider")
@Scope(ScopeType.STATELESS)
@BypassInterceptors
@Install(precedence=FRAMEWORK, classDependencies={"org.hibernate.Session", "javax.persistence.EntityManager"})
public class HibernatePersistenceProvider extends AbstractPersistenceProvider
{
   
   private static Log log = Logging.getLog(HibernatePersistenceProvider.class);
   private static Constructor FULL_TEXT_SESSION_PROXY_CONSTRUCTOR;
   private static Method FULL_TEXT_SESSION_CONSTRUCTOR;
   private static Constructor FULL_TEXT_ENTITYMANAGER_PROXY_CONSTRUCTOR;
   private static Method FULL_TEXT_ENTITYMANAGER_CONSTRUCTOR;
   static
   {
      try
      {
         String version = null;
         try {
            Class searchVersionClass = Class.forName("org.hibernate.search.Version");
            Field versionField = searchVersionClass.getDeclaredField("VERSION");
            version = (String) versionField.get(null);
         }
         catch (Exception e)
         {
            log.debug("no Hibernate Search, sorry :-(", e);
         }
         if (version != null) {
            Class searchClass = Class.forName("org.hibernate.search.Search");
            FULL_TEXT_SESSION_CONSTRUCTOR = searchClass.getDeclaredMethod("createFullTextSession", Session.class);
            Class fullTextSessionProxyClass = Class.forName("org.jboss.seam.persistence.FullTextHibernateSessionProxy");
            Class fullTextSessionClass = Class.forName("org.hibernate.search.FullTextSession");
            FULL_TEXT_SESSION_PROXY_CONSTRUCTOR = fullTextSessionProxyClass.getDeclaredConstructor(fullTextSessionClass);
            Class jpaSearchClass = Class.forName("org.hibernate.search.jpa.Search");
            FULL_TEXT_ENTITYMANAGER_CONSTRUCTOR = jpaSearchClass.getDeclaredMethod("createFullTextEntityManager", EntityManager.class);
            Class fullTextEntityManagerProxyClass = Class.forName("org.jboss.seam.persistence.FullTextEntityManagerProxy");
            Class fullTextEntityManagerClass = Class.forName("org.hibernate.search.jpa.FullTextEntityManager");
            FULL_TEXT_ENTITYMANAGER_PROXY_CONSTRUCTOR = fullTextEntityManagerProxyClass.getDeclaredConstructor(fullTextEntityManagerClass);
            log.debug("Hibernate Search is available :-)");
         }
      }
      catch (Exception e)
      {
         log.debug("no Hibernate Search, sorry :-(", e);
      }
   }
   
   /**
    * Wrap the Hibernate Session in a proxy that supports HQL
    * EL interpolation and implements FullTextSession if Hibernate
    * Search is available in the classpath.
    */
   static Session proxySession(Session session)
   {
      if (FULL_TEXT_SESSION_PROXY_CONSTRUCTOR==null)
      {
         return new HibernateSessionProxy(session);
      }
      else
      {
         try {
            return (Session) FULL_TEXT_SESSION_PROXY_CONSTRUCTOR.newInstance( FULL_TEXT_SESSION_CONSTRUCTOR.invoke(null, session) );
         }
         catch(Exception e) {
            log.warn("Unable to wrap into a FullTextSessionProxy, regular SessionProxy returned", e);
            return new HibernateSessionProxy(session);
         }
      }
   }
   
   /**
    * Wrap the delegate Hibernate Session in a proxy that supports HQL
    * EL interpolation and implements FullTextSession if Hibernate
    * Search is available in the classpath.
    */
   @Override
   public Object proxyDelegate(Object delegate)
   {
      try
      {
         return proxySession( (Session) delegate );
      }
      catch (Exception e)
      {
         throw new RuntimeException("could not proxy delegate", e);
      }
   }
   
   @Override
   public void setFlushModeManual(EntityManager entityManager)
   {
       getSession(entityManager).setFlushMode(FlushMode.MANUAL);
   }
   
   @Override
   public boolean isDirty(EntityManager entityManager)
   {
       return getSession(entityManager).isDirty();
   }
   
   @Override
   public Object getId(Object bean, EntityManager entityManager) 
   {
       try
       {
          return getSession(entityManager).getIdentifier(bean);
       }
       catch (TransientObjectException e) 
       {
          return super.getId(bean, entityManager);
       }
   }
   
   @Override
   public Object getVersion(Object bean, EntityManager entityManager) 
   {
       return getVersion( bean, getSession(entityManager) );
   }
   
   @Override
   public void checkVersion(Object bean, EntityManager entityManager, Object oldVersion, Object version)
   {
       checkVersion(bean, getSession(entityManager), oldVersion, version);
   }
   
   @Override
   public void enableFilter(Filter f, EntityManager entityManager)
   {
      org.hibernate.Filter filter = getSession(entityManager).enableFilter( f.getName() );
      for ( Map.Entry<String, ValueExpression> me: f.getParameters().entrySet() )
      {
         filter.setParameter( me.getKey(), me.getValue().getValue() );
      }
   }
   
   @Override
   public boolean registerSynchronization(Synchronization sync, EntityManager entityManager)
   {
      //TODO: just make sure that a Hibernate JPA EntityTransaction
      //      delegates to the Hibernate Session transaction
      getSession(entityManager).getTransaction().registerSynchronization(sync);
      return true;
   }

   @Override
   public String getName(Object bean, EntityManager entityManager) throws IllegalArgumentException
   {
      try 
      {
         return getSession(entityManager).getEntityName(bean);
      } 
      catch (TransientObjectException e) 
      {
         return super.getName(bean, entityManager);
      }
   }
   
   @Override
   public EntityManager proxyEntityManager(EntityManager entityManager)
   {
      if (FULL_TEXT_ENTITYMANAGER_PROXY_CONSTRUCTOR==null)
      {
         return super.proxyEntityManager(entityManager);
      }
      else
      {
         try
         {
            return (EntityManager) FULL_TEXT_ENTITYMANAGER_PROXY_CONSTRUCTOR.newInstance(
					FULL_TEXT_ENTITYMANAGER_CONSTRUCTOR.invoke(null, super.proxyEntityManager( entityManager) )
					//TODO is double wrapping the right choice? ie to wrap the session?
			);
		 }
         catch (Exception e)
         {
            //throw new RuntimeException("could not proxy FullTextEntityManager", e);
            return super.proxyEntityManager(entityManager);
         }
      }
   }
   
   public static void checkVersion(Object value, Session session, Object oldVersion, Object version)
   {
      ClassMetadata classMetadata = getClassMetadata(value, session);
      VersionType versionType = (VersionType) classMetadata.getPropertyTypes()[ classMetadata.getVersionProperty() ];
      if ( !versionType.isEqual(oldVersion, version) )
      {
         throw new StaleStateException("current database version number does not match passivated version number");
      }
   }
   
   public static Object getVersion(Object value, Session session)
   {
      ClassMetadata classMetadata = getClassMetadata(value, session);
      return classMetadata!=null && classMetadata.isVersioned() ? 
               classMetadata.getVersion(value, EntityMode.POJO) : null;
   }
   
   private static ClassMetadata getClassMetadata(Object value, Session session)
   {
      Class entityClass = Seam.getEntityClass( value.getClass() );
      ClassMetadata classMetadata = null;
      if (entityClass!=null)
      {
         classMetadata = session.getSessionFactory().getClassMetadata(entityClass);
         if (classMetadata==null)
         {
            throw new IllegalArgumentException( 
                     "Could not find ClassMetadata object for entity class: " + 
                     entityClass.getName() 
                  );
         }
      }
      return classMetadata;
   }
   
   /**
    * Returns the class of the specified Hibernate entity
    */
   @Override
   public Class getBeanClass(Object bean)
   {
      try
      {
         return super.getBeanClass(bean);
      }
      catch (IllegalArgumentException iae)
      {
         return Hibernate.getClass(bean);
      }
   }
   
   /**
    * A nasty hack until we get a nicer method in Hibernate to use instead
    * 
    * TODO fix this once Hibernate exposes an API method to return the callback method/s for a
    * given bean class
    * 
    * @param entityManager
    * @return
    */
   private EntityCallbackHandler getCallbackHandler(EntityManager entityManager)
   {
      PostLoadEventListener[] listeners = ((SessionImplementor) getSession(entityManager))
      .getListeners().getPostLoadEventListeners();
   
      for (PostLoadEventListener listener : listeners)
      {
         if (listener instanceof EJB3PostLoadEventListener)
         {
            try
            {
               Field callbackHandlerField = EJB3PostLoadEventListener.class.getField("callbackHandler");
               return (EntityCallbackHandler) callbackHandlerField.get(listener);
            }
            catch (Exception ex)
            {
               throw new RuntimeException(ex);
            }
         }
      }   
      return null;
   }
   
   /**
    * More nastiness
    * 
    * @param handler
    * @param fieldName
    * @return
    */
   private Callback[] getCallbacks(EntityCallbackHandler handler, String fieldName, Class beanClass)
   {
      try
      {
         Field f = EntityCallbackHandler.class.getField(fieldName);
         HashMap<Class,Callback[]> callbacks = (HashMap<Class,Callback[]>) f.get(handler);
         return callbacks.get(beanClass);
      }
      catch (Exception ex)
      {
         throw new RuntimeException(ex);
      }
   }
   
   private Method getCallbackMethod(EntityManager entityManager, Class beanClass, String callbackFieldName)
   {
      Callback[] callbacks = getCallbacks(getCallbackHandler(entityManager), callbackFieldName, beanClass);
      
      if (callbacks != null)
      {
         for (Callback cb : callbacks)
         {
            return cb.getCallbackMethod();
         }
      }
         
      return null;      
   }
   
   /*
   @Override
   public Method getPostLoadMethod(Class beanClass, EntityManager entityManager)
   {
      return getCallbackMethod(entityManager, beanClass, "postLoads");
   }
   
   
   @Override
   public Method getPrePersistMethod(Class beanClass, EntityManager entityManager)
   {
      return getCallbackMethod(entityManager, beanClass, "preCreates");
   }
   
   @Override
   public Method getPreUpdateMethod(Class beanClass, EntityManager entityManager)
   {
      return getCallbackMethod(entityManager, beanClass, "preUpdates");
   }
   
   @Override
   public Method getPreRemoveMethod(Class beanClass, EntityManager entityManager)
   {
      return getCallbackMethod(entityManager, beanClass, "preRemoves");
   }*/
   
   private Session getSession(EntityManager entityManager)
   {
      return (Session) entityManager.getDelegate(); 
   }
   
   public static HibernatePersistenceProvider instance()
   {
      return (HibernatePersistenceProvider) Component.getInstance(HibernatePersistenceProvider.class, STATELESS);
   }
   
}
