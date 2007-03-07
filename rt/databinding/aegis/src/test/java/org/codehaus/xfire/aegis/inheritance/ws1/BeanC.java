/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.codehaus.xfire.aegis.inheritance.ws1;

/**
 * <br/>
 * 
 * @author xfournet
 */
public class BeanC
    extends BeanB
{
    private String m_propC;
    private BeanD[] m_tabC;

    public String getPropC()
    {
        return m_propC;
    }

    public void setPropC(String propC)
    {
        m_propC = propC;
    }

    public BeanD[] getTabC()
    {
        return m_tabC;
    }

    public void setTabC(BeanD[] tabC)
    {
        this.m_tabC = tabC;
    }

    public String toString()
    {
        return super.toString() + " ; propC=" + m_propC;
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        if (!super.equals(o))
        {
            return false;
        }

        final BeanC beanC = (BeanC) o;

        if ((m_propC != null) ? (!m_propC.equals(beanC.m_propC)) : (beanC.m_propC != null))
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 29 * result + (m_propC != null ? m_propC.hashCode() : 0);
        return result;
    }
}
