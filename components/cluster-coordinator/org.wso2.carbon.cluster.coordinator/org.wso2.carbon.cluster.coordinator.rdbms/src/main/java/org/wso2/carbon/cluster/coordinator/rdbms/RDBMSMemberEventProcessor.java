/*
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.cluster.coordinator.rdbms;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.cluster.coordinator.commons.MemberEventListener;
import org.wso2.carbon.cluster.coordinator.commons.configs.CoordinationPropertyNames;
import org.wso2.carbon.cluster.coordinator.commons.exception.ClusterCoordinationException;
import org.wso2.carbon.cluster.coordinator.commons.util.MemberEventType;
import org.wso2.carbon.cluster.coordinator.rdbms.beans.StrategyConfig;
import org.wso2.carbon.cluster.coordinator.rdbms.internal.RDBMSCoordinationServiceHolder;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This class adds the event listener tasks for each group and executes them.
 */
public class RDBMSMemberEventProcessor {
    /**
     * Class log.
     */
    private static final Log log = LogFactory.getLog(RDBMSMemberEventProcessor.class);

    /**
     * Task map used store membership listener tasks.
     */
    private RDBMSMemberEventListenerTask membershipListenerTask;

    /**
     * Executor service used to run the event listening task.
     */
    private ScheduledExecutorService clusterMembershipReaderTaskScheduler;

    /**
     * Communication bus object to communicate with the database for the context store.
     */
    private RDBMSCommunicationBusContextImpl communicationBusContext;

    public RDBMSMemberEventProcessor(String localNodeId, String localGroupId, int heartbeatMaxRetry,
                                     RDBMSCommunicationBusContextImpl communicationBusContext) {
        this.communicationBusContext = communicationBusContext;
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ClusterEventReaderTask-%d").build();
        this.clusterMembershipReaderTaskScheduler = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
        addNewListenerTask(localNodeId, localGroupId, heartbeatMaxRetry);
    }

    /**
     * Method to start the membership listener task.
     *
     * @param nodeId the node ID of the node which starts the listening
     */
    private void addNewListenerTask(String nodeId, String localGroupId, int heartbeatMaxRetry) {
        StrategyConfig strategyConfiguration = RDBMSCoordinationServiceHolder.getClusterConfiguration().
                getStrategyConfig();
        int scheduledPeriod;
        if (strategyConfiguration != null) {
            scheduledPeriod = strategyConfiguration.getEventPollingInterval();
        } else {
            throw new ClusterCoordinationException("Cluster Configurations not found in" +
                    " deployment.yaml, please check " + CoordinationPropertyNames.CLUSTER_CONFIG_NS +
                    " namespace configurations");
        }
        membershipListenerTask = new RDBMSMemberEventListenerTask(nodeId, localGroupId, heartbeatMaxRetry,
                communicationBusContext);
        this.clusterMembershipReaderTaskScheduler.scheduleWithFixedDelay(membershipListenerTask,
                scheduledPeriod, scheduledPeriod, TimeUnit.MILLISECONDS);
        if (log.isDebugEnabled()) {
            log.debug("RDBMS cluster event listener started for node " + nodeId);
        }
    }

    /**
     * Method to stop the membership listener task.
     */
    public void stop() {
        clusterMembershipReaderTaskScheduler.shutdown();
    }

    /**
     * Notifies the other members in the group about the membership events.
     *
     * @param nodeID              the group id which triggered the event
     * @param groupID             the node id which triggered the event
     * @param nodes               The node list which the event should be updated to
     * @param membershipEventType the type of the membership event as an int
     * @throws ClusterCoordinationException
     */
    public void notifyMembershipEvent(String nodeID, String groupID, List<String> nodes,
                                      MemberEventType membershipEventType) throws ClusterCoordinationException {
        this.communicationBusContext.storeMembershipEvent(nodeID, groupID, nodes, membershipEventType.getCode());
    }

    /**
     * Add a listener to be notified of the cluster membership events.
     *
     * @param membershipListener membership listener object
     */
    public void addEventListener(MemberEventListener membershipListener) {
        membershipListenerTask.addEventListener(membershipListener);
    }

    /**
     * Remove a previously added listener.
     *
     * @param membershipListener membership listener object
     */
    public void removeEventListener(MemberEventListener membershipListener) {
        membershipListenerTask.removeEventListener(membershipListener);
    }
}
