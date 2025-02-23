package com.runflow.engine.behavior;

import com.runflow.engine.RunFlowException;
import com.runflow.engine.ExecutionEntityImpl;
import com.runflow.engine.cache.impl.CurrentHashMapCache;
import com.runflow.engine.context.Context;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.ParallelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ParallelGatewayActivityBehavior extends GatewayActivityBehavior {


    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelGatewayActivityBehavior.class);

    @Override
    public synchronized void execute(ExecutionEntityImpl e1) {

        ExecutionEntityImpl execution = e1;
        execution.inactivate();

        // Join
        FlowElement flowElement = execution.getCurrentFlowElement();

        ParallelGateway parallelGateway = null;
        if (flowElement instanceof ParallelGateway) {
            parallelGateway = (ParallelGateway) flowElement;
        } else {
            throw new RunFlowException("Programmatic error: parallel gateway behaviour can only be applied" + " to a ParallelGateway instance, but got an instance of " + flowElement);
        }



        int nbrOfExecutionsToJoin = parallelGateway.getIncomingFlows().size();
        CurrentHashMapCache<ExecutionEntityImpl> defaultSession = Context.getCommandContext().getDefaultSession();
        Set<ExecutionEntityImpl> entitySet = defaultSession.get(execution.getSerialNumber());

        List<ExecutionEntityImpl> joinedExecutions = entitySet.stream().filter(c -> !c.isActive() && c.getCurrentActivityId().equals(execution.getCurrentActivityId())).collect(Collectors.toList());


        int nbrOfExecutionsCurrentlyJoined = joinedExecutions.size();

        if (nbrOfExecutionsCurrentlyJoined == nbrOfExecutionsToJoin) {
            LOGGER.debug("并行网关  名称：{}  id:{}  线程名称:{} ", execution.getCurrentFlowElement().getName(), execution.getId(), Thread.currentThread().getName());
            if (parallelGateway.getIncomingFlows().size() > 1) {
                // All (now inactive) children are deleted.
                Iterator<ExecutionEntityImpl> iterator = joinedExecutions.iterator();
                while (iterator.hasNext()) {
                    ExecutionEntityImpl next = iterator.next();
                    if (!next.getId().equals(execution.getId())) {
                        entitySet.remove(next);
                    }
                }
            }
            Context.getAgenda().planTakeOutgoingSequenceFlowsOperation(execution, false); // false -> ignoring conditions on parallel gw
        }
    }




}
