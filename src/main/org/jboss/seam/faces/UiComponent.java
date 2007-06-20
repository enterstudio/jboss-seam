package org.jboss.seam.faces;

import static org.jboss.seam.ScopeType.STATELESS;
import static org.jboss.seam.annotations.Install.BUILT_IN;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;

import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.Unwrap;
import org.jboss.seam.annotations.intercept.BypassInterceptors;

@Name("org.jboss.seam.faces.uiComponent")
@BypassInterceptors
@Scope(STATELESS)
@Install(precedence=BUILT_IN, classDependencies="javax.faces.context.FacesContext")
public class UiComponent
{
   
   @Unwrap
   public Map<String, UIComponent> getViewComponents()
   {
      return new AbstractMap<String, UIComponent>() 
      {

         @Override
         public Set<Map.Entry<String, UIComponent>> entrySet()
         {
            throw new UnsupportedOperationException();
         }

         @Override
         public UIComponent get(Object key)
         {
            if ( !(key instanceof String) ) return null;
            return FacesContext.getCurrentInstance().getViewRoot().findComponent( (String) key );
         }
         
      };
   }
   
}
