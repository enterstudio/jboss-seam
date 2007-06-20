package org.jboss.seam.interceptors;

import static org.jboss.seam.util.JSF.DATA_MODEL;
import static org.jboss.seam.util.JSF.getWrappedData;
import static org.jboss.seam.util.JSF.setWrappedData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.jboss.seam.Seam;
import org.jboss.seam.annotations.AroundInvoke;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.intercept.Interceptor;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.intercept.InvocationContext;
import org.jboss.seam.persistence.PersistenceContexts;
import org.jboss.seam.transaction.Transaction;
import org.jboss.seam.util.Reflections;

/**
 * Swizzles entity references around each invocation, maintaining
 * referential integrity even across passivation of the stateful 
 * bean or Seam-managed extended persistence context, and allowing 
 * for more efficient replication.
 * 
 * @author Gavin King
 *
 */
@Interceptor(around=BijectionInterceptor.class, stateless=true)
public class ManagedEntityIdentityInterceptor extends AbstractInterceptor
{
 
   //TODO: cache the non-ignored fields, probably on Component
   
   @AroundInvoke
   public Object aroundInvoke(InvocationContext ctx) throws Exception
   {
      entityIdsToRefs(ctx);
      try
      {
         return ctx.proceed();
      }
      finally
      {
         if ( !isTransactionRolledBackOrMarkedRollback() )
         {
            entityRefsToIds(ctx);
         }
      }
   }
   
   private static boolean isTransactionRolledBackOrMarkedRollback()
   {
      try
      {
         return Transaction.instance().isRolledBackOrMarkedRollback();
      }
      catch (Exception e)
      {
         return false;
      }
   }
   
   public void entityRefsToIds(InvocationContext ctx) throws Exception
   {
      if ( touchedContextsExist() )
      {
         Object bean = ctx.getTarget();
         Class beanClass = bean.getClass();
         for (; beanClass!=Object.class; beanClass=beanClass.getSuperclass())
         {
            for ( Field field: beanClass.getDeclaredFields() )
            {
               if ( !ignore(field) )
               {
                  Object value = getFieldValue(bean, field);
                  if (value!=null)
                  {
                     Object dataModel = null;
                     if ( DATA_MODEL.isInstance(value) )
                     {
                        dataModel = value;
                        value = getWrappedData(dataModel);
                     }
                     if ( isRef(value) )
                     {
                        saveWrapper(bean, field, dataModel, value);
                     }
                  }
               }
            }
         }
      }
   }

   public void entityIdsToRefs(InvocationContext ctx) throws Exception
   {      
      if ( touchedContextsExist() )
      {
         Object bean = ctx.getTarget();
         Class beanClass = bean.getClass();
         for (; beanClass!=Object.class; beanClass=beanClass.getSuperclass())
         {
            for ( Field field: beanClass.getDeclaredFields() )
            {
               if ( !ignore(field) )
               {
                  Object value = getFieldValue(bean, field);
                  Object dataModel = null;
                  if (value!=null && DATA_MODEL.isInstance(value) )
                  {
                     dataModel = value;
                  }
                  //TODO: be more selective
                  getFromWrapper(bean, field, dataModel);
               }
            }
         }
      }
   }

   private boolean isRef(Object value)
   {
      //TODO: can do better than this for lists!
      return value instanceof List || Seam.isEntityClass( value.getClass() );
   }

   private Object getFieldValue(Object bean, Field field) throws Exception
   {
      if ( !field.isAccessible() ) field.setAccessible(true);
      Object value = Reflections.get(field, bean);
      return value;
   }

   private boolean ignore(Field field)
   {
      return Modifier.isTransient( field.getModifiers() ) || 
            Modifier.isStatic( field.getModifiers() )
            || field.isAnnotationPresent(In.class);
   }

   private boolean touchedContextsExist()
   {
      PersistenceContexts touchedContexts = PersistenceContexts.instance();
      return touchedContexts!=null && touchedContexts.getTouchedContexts().size()>0;
   }

   private String getFieldId(Field field)
   {
      return getComponent().getName() + '.' + field.getName();
   }

   private void saveWrapper(Object bean, Field field, Object dataModel, Object value) throws Exception
   {
      Contexts.getConversationContext().set( getFieldId(field), value );
      if (dataModel==null)
      {
         Reflections.set(field, bean, null);
      }
      else
      {
         setWrappedData(dataModel, null);
      }
   }

   private void getFromWrapper(Object bean, Field field, Object dataModel) throws Exception
   {
      Object value = Contexts.getConversationContext().get( getFieldId(field) );
      if (value!=null)
      {
         if (dataModel==null)
         {
            Reflections.set(field, bean, value);
         }
         else
         {
            setWrappedData(dataModel, value);
         }
      }
   }

}
