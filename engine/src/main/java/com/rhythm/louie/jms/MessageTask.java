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
package com.rhythm.louie.jms;

import java.io.IOException;
import java.util.*;

import javax.jms.*;

import com.google.common.base.Joiner;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhythm.louie.jms.JmsProtos.*;

/**
 *
 * @author sfong
 */
public class MessageTask implements Runnable {
    private static final String TYPE = "type";
    
    private final Logger LOGGER = LoggerFactory.getLogger(MessageTask.class);
            
    private final String service;
    private final JmsAdapter jmsAdapter;
    private final MessageAction action;
    private final Collection<Message> pbList;
    private final Map<String,String> headers;

    public MessageTask(String service, JmsAdapter jmsAdapter, MessageAction action, 
            Collection<Message> pbList, Map<String,String> headers) {
        this.service = service;
        this.jmsAdapter = jmsAdapter;
        this.action = action;
        this.pbList = pbList;
        if (headers==null) {
            this.headers = Collections.emptyMap();
        } else {
            this.headers = headers;
        }
    }
    
    public MessageBPB createMessage(MessageAction action, Message pb) {
        return createMessage(action, Collections.singletonList(pb));
    }
    
    public MessageBPB createMessage(MessageAction action, Collection<Message> pbList) {
        List<ContentPB> contentList = new ArrayList<>();
        for (Message pb : pbList) {
            ContentPB content = ContentPB.newBuilder()
                .setType(pb.getDescriptorForType().getFullName())
                .setContent(pb.toByteString())
                .build();
            contentList.add(content);
        }
        
        MessageBPB message = MessageBPB.newBuilder()
                .setAction(action.getAction())
                .addAllContent(contentList)
                .setTimestamp(System.currentTimeMillis())
                .build();
        return message;
    }
    
    @Override
    public void run() {
        Connection connect = null;
        try {
            Session session;
            Destination dest;
            String destinationName = MessagingProperties.getUpdatePrefix()+service;
            String destinationType = MessagingProperties.getUpdateType();
            // TODO ideally this should be more modular
            switch (destinationType) {
                case "queue":
                    QueueConnection queueConnect = jmsAdapter.getQueueConnectionFactory().createQueueConnection();
                    connect = queueConnect;
                    session = queueConnect.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
                    dest = session.createQueue(destinationName);
                    break;
                case "topic":
                    TopicConnection topicConnect = jmsAdapter.getTopicConnectionFactory().createTopicConnection();
                    connect = topicConnect;
                    session = topicConnect.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
                    dest = session.createTopic(destinationName);
                    break;
                default:
                    LOGGER.error("Unable to create message! Unknown Message Type: {}", destinationType);
                    return;
            }
            
            MessageProducer producer = session.createProducer(dest);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            
            Set<String> types = new HashSet<>();
            for (Message pb : pbList) {
                types.add(pb.getDescriptorForType().getFullName());
            }
            
            MessageBPB pb = createMessage(action, pbList);
            int size = pb.getSerializedSize();
            byte[] data = new byte[size];

            CodedOutputStream codedOutput = CodedOutputStream.newInstance(data);
            pb.writeTo(codedOutput);

            BytesMessage msg = session.createBytesMessage();
            msg.writeBytes(data);
            msg.setStringProperty(TYPE, Joiner.on(" ").join(types));
            
            for (Map.Entry<String,String> header : headers.entrySet()) {
                if (header.getKey().equals(TYPE)) {
                    LOGGER.warn("Skipping setting of reserved message property {}={}",
                            header.getKey(),header.getValue());
                } else {
                    msg.setStringProperty(header.getKey(), header.getValue());
                }
            }
            
            // TODO this should be a property
            //set message time-out to 24 hours
            producer.send(msg, DeliveryMode.PERSISTENT, 4, 86400000);
        } catch (JMSException | IOException e) {
            LOGGER.error("Error sending message",e);
        } finally {
            try {
                if (connect != null) {
                    connect.close();
                }
            } catch (Exception e) {
            }
        }
    }
    
}
