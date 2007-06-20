package org.jboss.seam.core;

import static org.jboss.seam.annotations.Install.BUILT_IN;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.Unwrap;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.log.LogProvider;
import org.jboss.seam.log.Logging;
import org.jboss.seam.navigation.Pages;
import org.jboss.seam.util.EnumerationEnumeration;
import org.jboss.seam.util.Strings;

/**
 * Support for an application-global resource bundle
 * 
 * @author Gavin King
 */
@Scope(ScopeType.SESSION)
@BypassInterceptors
@Name("org.jboss.seam.core.resourceBundle")
@Install(precedence=BUILT_IN)
public class ResourceBundle implements Serializable 
{
   
   protected java.util.Locale getCurrentLocale()
   {
      //TODO:
      return Locale.getDefault();
   }
   
   public class UberResourceBundle extends java.util.ResourceBundle
   {
      private final List<java.util.ResourceBundle> bundles;

      public UberResourceBundle(List<java.util.ResourceBundle> bundles)
      {
         this.bundles = bundles;
      }

      @Override
      public java.util.Locale getLocale()
      {
         return getCurrentLocale();
      }

      @Override
      public Enumeration<String> getKeys()
      {
         List<java.util.ResourceBundle> pageBundles = getPageResourceBundles();
         Enumeration<String>[] enumerations = new Enumeration[ bundles.size() + pageBundles.size() ];
         int i=0;
         for (; i<pageBundles.size(); i++)
         {
            enumerations[i++] = pageBundles.get(i).getKeys();
         }
         for (; i<bundles.size(); i++)
         {
            enumerations[i] = bundles.get(i).getKeys();
         }
         return new EnumerationEnumeration<String>(enumerations);
      }

      @Override
      protected Object handleGetObject(String key)
      {
         List<java.util.ResourceBundle> pageBundles = getPageResourceBundles();
         for (java.util.ResourceBundle pageBundle: pageBundles)
         {
            try
            {
               return pageBundle.getObject(key);
            }
            catch (MissingResourceException mre) {}
         }
         
         for (java.util.ResourceBundle littleBundle: bundles)
         {
            if (littleBundle!=null)
            {
               try
               {
                  return littleBundle.getObject(key);
               }
               catch (MissingResourceException mre) {}
            }
         }
         
         return null; //superclass is responsible for throwing MRE
      }

      private List<java.util.ResourceBundle> getPageResourceBundles()
      {
         String viewId = Pages.getCurrentViewId();
         if (viewId!=null)
         {
            return Pages.instance().getResourceBundles(viewId);
         }
         else
         {
            return Collections.EMPTY_LIST;
         }
      }
   }

   private static final long serialVersionUID = -3236251335438092538L;
   private static final LogProvider log = Logging.getLogProvider(ResourceBundle.class);

   private String[] bundleNames = {"messages"};
   private transient java.util.ResourceBundle bundle;

   public String[] getBundleNames() 
   {
      return bundleNames;
   }
   
   public void setBundleNames(String[] bundleNames) 
   {
      this.bundleNames = bundleNames;
   }
   
   @Deprecated
   public void setBundleName(String bundleName)
   {
      bundleNames = bundleName==null ? null : new String[] { bundleName };
   }
   
   @Deprecated
   public String getBundleName()
   {
      return bundleNames==null || bundleNames.length==0 ? null : bundleNames[0];
   }
   
   /**
    * Load a resource bundle by name (may be overridden by subclasses
    * who want to use non-standard resource bundle types).
    * 
    * @param bundleName the name of the resource bundle
    * @return an instance of java.util.ResourceBundle
    */
   protected java.util.ResourceBundle loadBundle(String bundleName) 
   {
      try
      {
         java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle( 
               bundleName, 
               getCurrentLocale(), 
               Thread.currentThread().getContextClassLoader() 
            );
         log.debug("loaded resource bundle: " + bundleName);
         return bundle;
      }
      catch (MissingResourceException mre)
      {
         log.debug("resource bundle missing: " + bundleName);
         return null;
      }
   }
   
   private void createUberBundle()
   {
      final List<java.util.ResourceBundle> littleBundles = new ArrayList<java.util.ResourceBundle>();
      if (bundleNames!=null)
      {  
         for (String bundleName: bundleNames)
         {
            java.util.ResourceBundle littleBundle = loadBundle(bundleName);
            if (littleBundle!=null) littleBundles.add(littleBundle);
         }
      }
      
      java.util.ResourceBundle validatorBundle = loadBundle("ValidatorMessages");
      if (validatorBundle!=null) littleBundles.add(validatorBundle);
      java.util.ResourceBundle validatorDefaultBundle = loadBundle("org/hibernate/validator/resources/DefaultValidatorMessages");
      if (validatorDefaultBundle!=null) littleBundles.add(validatorDefaultBundle);
         
      bundle = new UberResourceBundle(littleBundles);
  
   }

   @Unwrap
   public java.util.ResourceBundle getBundle()
   {
      if (bundle==null) createUberBundle();
      return bundle;
   }
   
   @Override
   public String toString()
   {
      String concat = bundleNames==null ? "" : Strings.toString( ", ", (Object[]) bundleNames );
      return "ResourceBundle(" + concat + ")";
   }

   public static java.util.ResourceBundle instance()
   {
      return (java.util.ResourceBundle) Component.getInstance(ResourceBundle.class, true);
   }
}
