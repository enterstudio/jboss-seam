/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.seam.ui.tag;

import javax.faces.component.UIComponent;

import org.jboss.seam.ui.HtmlDiv;


public class DivTag extends HtmlComponentTagBase
{
    public String getComponentType()
    {
        return HtmlDiv.COMPONENT_TYPE;
    }

    public String getRendererType()
    {
        return null;
    }

    private String styleClass;
    private String style;

    protected void setProperties(UIComponent component)
    {
        super.setProperties(component);
        setStringProperty(component, "styleClass", styleClass);
        setStringProperty(component, "style", style);
    }

   public void setStyle(String style)
   {
      this.style = style;
   }

   public void setStyleClass(String styleClass)
   {
      this.styleClass = styleClass;
   }

}
