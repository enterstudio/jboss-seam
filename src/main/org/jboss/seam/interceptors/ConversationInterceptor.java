//$Id$
package org.jboss.seam.interceptors;

import java.lang.reflect.Method;
import java.util.Arrays;

import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.Around;
import org.jboss.seam.annotations.Begin;
import org.jboss.seam.annotations.BeginTask;
import org.jboss.seam.annotations.End;
import org.jboss.seam.annotations.EndTask;
import org.jboss.seam.annotations.StartTask;
import org.jboss.seam.annotations.Within;
import org.jboss.seam.core.ConversationEntry;
import org.jboss.seam.core.Interpolator;
import org.jboss.seam.core.Manager;
import org.jboss.seam.core.Pageflow;

/**
 * After the end of the invocation, begin or end a long running
 * conversation, if necessary.
 * 
 * @author Gavin King
 */
@Around({ValidationInterceptor.class, BijectionInterceptor.class, OutcomeInterceptor.class})
@Within(BusinessProcessInterceptor.class)
public class ConversationInterceptor extends AbstractInterceptor
{

   private static final Log log = LogFactory.getLog(ConversationInterceptor.class);

   @AroundInvoke
   public Object endOrBeginLongRunningConversation(InvocationContext invocation) throws Exception
   {
      Method method = invocation.getMethod();

      if ( isMissingJoin(method) )
      {
         throw new IllegalStateException("begin method invoked from a long running conversation, try using @Begin(join=true)");
      }
      
      String outcome = getOutcomeForConversationId(method);
      if (outcome!=null) 
      {
         if ( !method.getReturnType().equals(String.class) )
         {
            throw new IllegalStateException("begin method return type was not a string");
         }
         return outcome;
      }

      Object result = invocation.proceed();

      beginConversationIfNecessary(method, result);
      endConversationIfNecessary(method, result);
      return result;
   
   }
   
   public String getOutcomeForConversationId(Method method)
   {
      String id = null;
      if ( method.isAnnotationPresent(Begin.class) )
      {
         id = method.getAnnotation(Begin.class).id();
      }
      else if ( method.isAnnotationPresent(BeginTask.class) )
      {
         id = method.getAnnotation(BeginTask.class).id();
      }
      else if ( method.isAnnotationPresent(StartTask.class) )
      {
         id = method.getAnnotation(StartTask.class).id();
      }
      
      if ( id!=null && !"".equals(id) )
      {
         id = Interpolator.instance().interpolate(id);
         ConversationEntry ce = Manager.instance().getConversationIdEntryMap().get(id);
         if (ce==null) 
         {
            Manager.instance().updateCurrentConversationId(id);
         }
         else
         {
            return ce.select();
         }
      }
      
      return null;
   }

   private boolean isMissingJoin(Method method) {
      return Manager.instance().isLongRunningConversation() && ( 
            ( 
                  method.isAnnotationPresent(Begin.class) && 
                  !method.getAnnotation(Begin.class).join() && 
                  !method.getAnnotation(Begin.class).nested() 
            ) ||
            method.isAnnotationPresent(BeginTask.class) ||
            method.isAnnotationPresent(StartTask.class) 
         );
   }

   private void beginConversationIfNecessary(Method method, Object result)
   {
      
      boolean simpleBegin = 
            method.isAnnotationPresent(StartTask.class) || 
            method.isAnnotationPresent(BeginTask.class) ||
            ( method.isAnnotationPresent(Begin.class) && method.getAnnotation(Begin.class).ifOutcome().length==0 );
      if ( simpleBegin )
      {
         if ( result!=null || method.getReturnType().equals(void.class) )
         {
            boolean nested = false;
            if ( method.isAnnotationPresent(Begin.class) )
            {
               nested = method.getAnnotation(Begin.class).nested();
            }
            beginConversation( nested, getProcessDefinitionName(method) );
         }
      }
      else if ( method.isAnnotationPresent(Begin.class) )
      {
         String[] outcomes = method.getAnnotation(Begin.class).ifOutcome();
         if ( outcomes.length==0 || Arrays.asList(outcomes).contains(result) )
         {
            beginConversation( 
                  method.getAnnotation(Begin.class).nested(), 
                  getProcessDefinitionName(method) 
               );
         }
      }
      
   }

   private String getProcessDefinitionName(Method method) {
      if ( method.isAnnotationPresent(Begin.class) )
      {
         return method.getAnnotation(Begin.class).pageflow();
      }
      if ( method.isAnnotationPresent(BeginTask.class) )
      {
         return method.getAnnotation(BeginTask.class).pageflow();
      }
      if ( method.isAnnotationPresent(StartTask.class) )
      {
         return method.getAnnotation(StartTask.class).pageflow();
      }
      //TODO: let them pass a pagelfow name as a request parameter
      return "";
   }

   private void beginConversation(boolean nested, String pageflowName)
   {
      if ( !Manager.instance().isLongRunningConversation() )
      {
         log.debug("Beginning long-running conversation");
         Manager.instance().beginConversation( component.getName() );
         beginNavigation(pageflowName);
      }
      else if (nested)
      {
         log.debug("Beginning nested conversation");
         Manager.instance().beginNestedConversation( component.getName() );
         beginNavigation(pageflowName);
      }
   }
   
   private void beginNavigation(String pageflowName)
   {
      if ( !pageflowName.equals("") )
      {
         Pageflow.instance().begin(pageflowName);
      }
   }

   private void endConversationIfNecessary(Method method, Object result)
   {
      boolean simpleEnd = 
            ( method.isAnnotationPresent(End.class) && method.getAnnotation(End.class).ifOutcome().length==0 ) || 
            ( method.isAnnotationPresent(EndTask.class) && method.getAnnotation(EndTask.class).ifOutcome().length==0 );
      if ( simpleEnd )
      {
         if ( result!=null || method.getReturnType().equals(void.class) ) //null outcome interpreted as redisplay
         {
            endConversation();
         }
      }
      else if ( method.isAnnotationPresent(End.class) )
      {
         String[] outcomes = method.getAnnotation(End.class).ifOutcome();
         if ( Arrays.asList(outcomes).contains(result) )
         {
            endConversation();
         }
      }
      else if ( method.isAnnotationPresent(EndTask.class) )
      {
         //TODO: fix minor code duplication
         String[] outcomes = method.getAnnotation(EndTask.class).ifOutcome();
         if ( Arrays.asList(outcomes).contains(result) )
         {
            endConversation();
         }
      }
   }

   private void endConversation()
   {
      log.debug("Ending long-running conversation");
      Manager.instance().endConversation();
   }

}
