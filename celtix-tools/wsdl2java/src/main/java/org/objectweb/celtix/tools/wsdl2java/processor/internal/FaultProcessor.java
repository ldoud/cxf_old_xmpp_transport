package org.objectweb.celtix.tools.wsdl2java.processor.internal;

import java.util.Collection;
import java.util.Map;

import javax.wsdl.Fault;
import javax.wsdl.Message;
import javax.wsdl.Part;

import com.sun.tools.xjc.api.S2JJAXBModel;

import org.objectweb.celtix.tools.common.ProcessorEnvironment;
import org.objectweb.celtix.tools.common.ToolConstants;
import org.objectweb.celtix.tools.common.ToolException;
import org.objectweb.celtix.tools.common.model.JavaException;
import org.objectweb.celtix.tools.common.model.JavaExceptionClass;
import org.objectweb.celtix.tools.common.model.JavaField;
import org.objectweb.celtix.tools.common.model.JavaMethod;
import org.objectweb.celtix.tools.common.model.JavaModel;
import org.objectweb.celtix.tools.util.ProcessorUtil;

public class FaultProcessor extends AbstractProcessor {

   
    public FaultProcessor(ProcessorEnvironment penv) {
      super(penv);
    }

    public void process(JavaMethod method, Map<String, Fault> faults) throws ToolException {
        if (faults == null) {
            return;
        }

        Collection<Fault> faultsValue = faults.values();
        for (Fault fault : faultsValue) {
            processFault(method, fault);
        }
    }

    private boolean isNameCollision(String packageName, String className) {  
        boolean collision = collector.containTypesClass(packageName, className)
            || collector.containSeiClass(packageName, className);
        return collision;
    }

    @SuppressWarnings("unchecked")
    private void processFault(JavaMethod method, Fault fault) throws ToolException {
        JavaModel model = method.getInterface().getJavaModel();
        Message faultMessage = fault.getMessage();
        String name = ProcessorUtil.mangleNameToClassName(faultMessage.getQName().getLocalPart());
        String namespace = faultMessage.getQName().getNamespaceURI();
        String packageName = ProcessorUtil.parsePackageName(namespace, env.mapPackageName(namespace));

        while (isNameCollision(packageName, name)) {
            name = name + "_Exception";
        }
        
        String fullClassName = packageName + "." + name;
        collector.addExceptionClassName(packageName, name, fullClassName);        

        boolean samePackage = method.getInterface().getPackageName().equals(packageName);
        method.addException(new JavaException(name, samePackage ? name : fullClassName, namespace));
        
        Map<String, Part> faultParts = faultMessage.getParts();
        Collection<Part> faultValues = faultParts.values();
        
        JavaExceptionClass expClass = new JavaExceptionClass(model);
        expClass.setName(name);
        expClass.setNamespace(namespace);
        expClass.setPackageName(packageName);
        S2JJAXBModel jaxbModel = (S2JJAXBModel)env.get(ToolConstants.RAW_JAXB_MODEL);
        for (Part part : faultValues) {

            String fName = part.getName();
            String fType = ProcessorUtil.resolvePartType(part, jaxbModel);
            String fNamespace = ProcessorUtil.resolvePartNamespace(part);
            String fPackageName = ProcessorUtil.parsePackageName(fNamespace, env.mapPackageName(fNamespace));

            JavaField fField = new JavaField(fName, fType, fNamespace);
            fField.setQName(ProcessorUtil.getElementName(part));
            
            if (!method.getInterface().getPackageName().equals(fPackageName)) {
                fField.setClassName(ProcessorUtil.getFullClzName(part, env, this.collector));                
            }
            if (!fType.equals(ProcessorUtil.resolvePartType(part))) {
                fField.setClassName(ProcessorUtil.resolvePartType(part, jaxbModel, true));
            }

            expClass.addField(fField);
        }
        model.addExceptionClass(name, expClass);
    }
}
