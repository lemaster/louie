/* 
 * Copyright 2015 Rhythm & Hues Studios.
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
package com.rhythm.louie.request;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.rhythm.louie.server.ServiceManager;
import com.rhythm.louie.service.Service;

import com.rhythm.louie.request.data.Param;
import com.rhythm.louie.request.data.Result;

/**
 * @author cjohnson
 Created: Jun 14, 2011 3:47:14 PM
 */
public class RequestHandler {
    public static void logRequest(RequestContext request,Result result) {
        Logger log = LoggerFactory.getLogger("louie.request");
        
        // Bail out of here if logging is turned off.
        if (result.isError() && !log.isErrorEnabled()) {
            return;
        } else if (!result.isError() && !log.isInfoEnabled()) {
            return;
        }
        
        String session = request.getWho();
        if (request.getRequest().hasRouteUser()) {
            session=request.getIdentity().getUser()+"("+session+")";
        }
        if (request.getSessionKey()!=null && !request.getSessionKey().isEmpty()) {
            session+="-"+request.getSessionKey().substring(0,Math.min(8,request.getSessionKey().length()));
        }
        MDC.put(LogVars.SESSION, session);
        MDC.put(LogVars.IP, request.getRequestProperties().getRemoteAddress());
        MDC.put(LogVars.MODULE, request.getModule());
        MDC.put(LogVars.LANGUAGE,request.getLanguage());
        MDC.put(LogVars.TIME,Long.toString(result.getDuration()));
        MDC.put(LogVars.EXECTIME,Long.toString(result.getExecTime()));
        MDC.put(LogVars.ROWS, Integer.toString(result.getMessages().size()));
        
        // Note this should be efficient if the message has been serialized,
        // since the size is cached, else this is unnecessarily expensive
        if (result.getSize()<=0) {
            long totalSize = 0;
            for (Message message : result.getMessages()) {
                totalSize+=message.getSerializedSize();
            }
            result.setSize(totalSize);
        }
        MDC.put(LogVars.BYTES, Long.toString(result.getSize()));
        
        StringBuilder logtext = new StringBuilder();
        logtext.append(request.getRequest().getService()).append(":")
             .append(request.getRequest().getMethod());
        logtext.append("(");
        if (request.getRequest().getTypeCount()>0) {
            Joiner.on(",").appendTo(logtext, request.getRequest().getTypeList());
        }
        logtext.append(")");
        
        if (!request.getParams().isEmpty()) {
            logtext.append(" - ");
            logtext.append("(");
            appendListString(logtext,request.getParams());
            logtext.append(")");
        }

        if (result.isError()) {
            log.error(logtext.toString());
        } else {
            if (request.getParams().size()>1) {
                log.warn(logtext.toString());
            } else {
                log.info(logtext.toString());
            }
        }
    }
    
    protected static void appendListString(StringBuilder logtext, Collection list) {
        boolean first = true;
        for (Object o : list) {
            if (first) {
                first = false;
            } else {
                logtext.append(",");
            }
           
            if (o instanceof Collection) {
                logtext.append("[");
                appendListString(logtext,(Collection)o);
                logtext.append("]");
            } else {
                appendString(logtext,o);
            }
        }
    }
    
    private static void appendString(StringBuilder logtext, Object o) {
        if (o instanceof Param) {
            Param p = ((Param)o);
            appendListString(logtext,p.getParsedArgs());
        } else if (o instanceof Message) {
            Message m = (Message) o;
            printMessage(m, logtext);
        } else {
            logtext.append(o);
        }
    }
    
    private static final int STRING_LIMIT = 100;
    private static final int BYTES_LIMIT = 20;
    private static final int LIST_LIMIT = 10;
    
    private static void printMessage(Message m, StringBuilder logtext) {
        for (Map.Entry<FieldDescriptor, Object> field : m.getAllFields().entrySet()) {
            try {
                printField(field.getKey(), field.getValue(), logtext);
            } catch (IOException ex) {
                logtext.append("ERROR LOGGING! ").append(field.getKey());
            }
        }
    }
  
    private static void printField(final FieldDescriptor field, final Object value,
            final StringBuilder generator) throws IOException {
        if (field.isRepeated()) {
            // Repeated field.  Print each element.
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                for (Object element : (List<?>) value) {
                    printSingleField(field, element, generator);
                }
            } else {
                generator.append(field.getName());
                generator.append(": [");
                int i=1;
                List<?> list = (List<?>) value;
                for (Object element : list) {
                    if (i>1) {
                        generator.append(",");
                    }
                    printFieldValue(field, element, generator);
                    if (i>=LIST_LIMIT) {
                        generator.append("...<").append(list.size()).append(">");
                        break;
                    }
                    i++;
                }
                generator.append("]");
            }
        } else {
            printSingleField(field, value, generator);
        }
    }

    private static void printSingleField(final FieldDescriptor field,
                                  final Object value,
                                  final StringBuilder generator)
                                  throws IOException {
     
      generator.append(field.getName());

      if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
          generator.append(" { ");
      } else {
        generator.append(": ");
      }
      
      printFieldValue(field, value, generator);

      if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
          generator.append("} ");
      } else {
          generator.append(" ");
      }
    }
    
    private static void printFieldValue(final FieldDescriptor field,
            Object value,
            final StringBuilder generator)
            throws IOException {
        
        boolean ellipses = false;
        int size = 0;
        switch (field.getType()) {
            case STRING:
                String s = (String) value;
                if (s.length() > STRING_LIMIT) {
                    size = s.length();
                    ellipses = true;
                    value = s.substring(0, STRING_LIMIT);
                }
                break;
            case BYTES:
                ByteString bs = (ByteString) value;
                if (bs.size() > BYTES_LIMIT) {
                    size = bs.size();
                    ellipses = true;
                    value = ByteString.copyFrom(bs.asReadOnlyByteBuffer(), BYTES_LIMIT);
                }
                break;
            case MESSAGE:
            case GROUP:
                printMessage((Message)value,generator);
                return;
        }
        TextFormat.printFieldValue(field, value, generator);
        
        if (ellipses) {
            generator.append("...<").append(size).append(">");
        }
    }
    
    public static Result processSingleRequest(RequestContext pbreq) throws Exception {

        if (pbreq.getRequest().getService() == null || pbreq.getRequest().getService().isEmpty()) {
            throw new Exception("No Service Specified");
        }
        if (pbreq.getRequest().getMethod() == null || pbreq.getRequest().getMethod().isEmpty()) {
            throw new Exception("No Method Specified");
        }

        MDC.put(LogVars.REQID, Integer.toString(pbreq.getRequest().getId()));
        
        try {
            RequestContextManager.setRequest(pbreq);
            
            String serviceName = pbreq.getRequest().getService();
            Service service = ServiceManager.getService(serviceName);
            if (service == null) {
                throw new Exception("No such service: " + serviceName);
            }
            
            Result result =  service.executeCommand(pbreq);
            if (result == null) {
                LoggerFactory.getLogger("").error("Result is null!?");
                result = Result.emptyResult();
            }
            return result;
        } catch (Exception e) {
            Result result = Result.errorResult(e);
            String command = pbreq.getRequestProperties().getGateway()+"/"
                    +pbreq.getRequest().getService() + ":" + pbreq.getRequest().getMethod();
            
            Throwable cause = e;
            if (e instanceof InvocationTargetException && e.getCause()!=null) {
                cause = e.getCause();
            }
            LoggerFactory.getLogger("").error("Unable to process request: "+command+"!",cause);
            return result;
        } finally {
            RequestContextManager.clearRequest();
        }
    }
}
