package com.ilimi.graph.model.collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.Response;
import com.ilimi.common.exception.ClientException;
import com.ilimi.common.exception.ResponseCode;
import com.ilimi.common.exception.ServerException;
import com.ilimi.graph.cache.mgr.impl.SequenceCacheManager;
import com.ilimi.graph.common.mgr.BaseGraphManager;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.RelationTypes;
import com.ilimi.graph.dac.enums.SystemNodeTypes;
import com.ilimi.graph.dac.enums.SystemProperties;
import com.ilimi.graph.dac.router.GraphDACActorPoolMgr;
import com.ilimi.graph.dac.router.GraphDACManagers;
import com.ilimi.graph.exception.GraphEngineErrorCodes;

import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class Sequence extends AbstractCollection {

    private List<String> memberIds;

    public Sequence(BaseGraphManager manager, String graphId, String id) {
        super(manager, graphId, id, null); // TODO: Will add metadata if required.
    }

    public Sequence(BaseGraphManager manager, String graphId, String id, List<String> memberIds) {
        super(manager, graphId, id, null); // TODO: Will add metadata if required.
        this.memberIds = memberIds;
    }

    @Override
    public void create(final Request req) {
        try {
            final ExecutionContext ec = manager.getContext().dispatcher();
            if (null != memberIds && memberIds.size() > 0) {
                Future<Boolean> validMembers = checkMemberNodes(req, memberIds, ec);
                validMembers.onComplete(new OnComplete<Boolean>() {
                    @Override
                    public void onComplete(Throwable arg0, Boolean arg1) throws Throwable {
                        if (null != arg0) {
                            manager.ERROR(arg0, getParent());
                        } else {
                            if (arg1) {
                                createSequenceObject(req, ec);
                            } else {
                                manager.ERROR(GraphEngineErrorCodes.ERR_GRAPH_CREATE_SEQUENCE_INVALID_MEMBERIDS.name(),
                                        "Member Ids are invalid", ResponseCode.CLIENT_ERROR, getParent());
                            }
                        }
                    }
                }, ec);
            } else {
                createSequenceObject(req, ec);
            }
        } catch (Exception e) {
            throw new ServerException(GraphEngineErrorCodes.ERR_GRAPH_CREATE_SEQUENCE_UNKNOWN_ERROR.name(), e.getMessage(), e);
        }
    }

    @Override
    public void addMember(final Request req) {
        final String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
        final String memberId = (String) req.get(GraphDACParams.member_id.name());
        if (!manager.validateRequired(sequenceId, memberId)) {
            throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_ADD_SEQUENCE_MEMBER_MISSING_REQ_PARAMS.name(),
                    "Required parameters are missing...");
        } else {
            try {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		Long index = SequenceCacheManager.addSequenceMember(graphId, sequenceId, null, memberId);
                if(null == index){
                    manager.ERROR(GraphEngineErrorCodes.ERR_GRAPH_ADD_SEQUENCE_MEMBER_UNKNOWN_ERROR.name(),
                            null, ResponseCode.CLIENT_ERROR, getParent());
                } else {
                    ActorRef dacRouter = GraphDACActorPoolMgr.getDacRouter();
                    Request dacRequest = new Request(req);
                    dacRequest.setManagerName(GraphDACManagers.DAC_GRAPH_MANAGER);
                    dacRequest.setOperation("addRelation");
                    dacRequest.put(GraphDACParams.start_node_id.name(), sequenceId);
                    dacRequest.put(GraphDACParams.relation_type.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
                    dacRequest.put(GraphDACParams.end_node_id.name(), memberId);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put(SystemProperties.IL_SEQUENCE_INDEX.name(), index);
                    dacRequest.put(GraphDACParams.metadata.name(), map);
                    dacRouter.tell(dacRequest, manager.getSelf());
                    manager.OK(getParent());
                }
            } catch (Exception e) {
                manager.handleException(e, getParent());
            }
        }
    }

    @Override
    public void removeMember(Request req) {
        try {
            String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
            String memberId = (String) req.get(GraphDACParams.member_id.name());
            if (!manager.validateRequired(sequenceId, memberId)) {
                throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_REMOVE_SEQUENCE_MEMBER_MISSING_REQ_PARAMS.name(),
                        "Required parameters are missing...");
            } else {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		SequenceCacheManager.removeSequenceMember(graphId, sequenceId, memberId);
         		Future<Object> response = null;
                ActorRef dacRouter = GraphDACActorPoolMgr.getDacRouter();
                Request dacRequest = new Request(req);
                dacRequest.setManagerName(GraphDACManagers.DAC_GRAPH_MANAGER);
                dacRequest.setOperation("deleteRelation");
                dacRequest.put(GraphDACParams.start_node_id.name(), sequenceId);
                dacRequest.put(GraphDACParams.relation_type.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
                dacRequest.put(GraphDACParams.end_node_id.name(), memberId);
                dacRouter.tell(dacRequest, manager.getSelf());
                manager.returnResponse(response, getParent());
            }
        } catch (Exception e) {
            manager.handleException(e, getParent());
        }
    }

    @Override
    public void getMembers(Request req) {
        try {
            String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
            if (!manager.validateRequired(sequenceId)) {
                throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_GET_SEQUENCE_MEMBERS_INVALID_SEQID.name(),
                        "Required parameters are missing...");
            } else {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		List<String> members = SequenceCacheManager.getSequenceMembers(graphId, sequenceId);
         		manager.OK(GraphDACParams.members.name(), members, getParent());
            }
        } catch (Exception e) {
            manager.handleException(e, getParent());
        }
    }

    @Override
    public void isMember(Request req) {
        try {
            String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
            String memberId = (String) req.get(GraphDACParams.member_id.name());
            if (!manager.validateRequired(sequenceId, memberId)) {
                throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_IS_SEQUENCE_MEMBER_MISSING_REQ_PARAMS.name(),
                        "Required parameters are missing...");
            } else {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		Boolean isMember= SequenceCacheManager.isSequenceMember(graphId, sequenceId, memberId);
         		manager.OK(GraphDACParams.is_member.name(),isMember,getParent());
            }
        } catch (Exception e) {
            manager.handleException(e, getParent());
        }
    }

    @Override
    public void delete(Request req) {
        try {
            String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
            if (!manager.validateRequired(sequenceId)) {
                throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_DROP_SEQUENCE_MISSING_REQ_PARAMS.name(),
                        "Required parameters are missing...");
            } else {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		SequenceCacheManager.dropSequence(graphId, sequenceId);
         		Future<Object> response = null;
                ActorRef dacRouter = GraphDACActorPoolMgr.getDacRouter();
                Request dacRequest = new Request(req);
                dacRequest.setManagerName(GraphDACManagers.DAC_GRAPH_MANAGER);
                dacRequest.setOperation("deleteCollection");
                dacRequest.put(GraphDACParams.collection_id.name(), sequenceId);
                dacRouter.tell(dacRequest, manager.getSelf());

                manager.returnResponse(response, getParent());
            }
        } catch (Exception e) {
            manager.handleException(e, getParent());
        }
    }

    @Override
    public void getCardinality(Request req) {
        try {
            String sequenceId = (String) req.get(GraphDACParams.collection_id.name());
            if (!manager.validateRequired(sequenceId)) {
                throw new ClientException(GraphEngineErrorCodes.ERR_GRAPH_COLLECTION_GET_CARDINALITY_MISSING_REQ_PARAMS.name(),
                        "Required parameters are missing...");
            } else {
            	req.getContext().get(GraphDACParams.graph_id.name());
         		Long cardinality= SequenceCacheManager.getSequenceCardinality(graphId, sequenceId);
         		manager.OK(GraphDACParams.cardinality.name(), cardinality, getParent());
            }
        } catch (Exception e) {
            manager.handleException(e, getParent());
        }
    }

    @Override
    public String getSystemNodeType() {
        return SystemNodeTypes.SEQUENCE.name();
    }

    private void createSequenceObject(final Request req, final ExecutionContext ec) {
        final ActorRef dacRouter = GraphDACActorPoolMgr.getDacRouter();
        Request request = new Request(req);
        request.setManagerName(GraphDACManagers.DAC_NODE_MANAGER);
        request.setOperation("addNode");
        request.put(GraphDACParams.node.name(), toNode());
        Future<Object> dacFuture = Patterns.ask(dacRouter, request, timeout);

        dacFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable arg0, Object arg1) throws Throwable {
                if (null != arg0) {
                    manager.ERROR(arg0, getParent());
                } else {
                    if (arg1 instanceof Response) {
                        Response res = (Response) arg1;
                        if (manager.checkError(res)) {
                            manager.ERROR(GraphEngineErrorCodes.ERR_GRAPH_CREATE_SEQUENCE_UNKNOWN_ERROR.name(),
                                    manager.getErrorMessage(res), res.getResponseCode(), getParent());
                        } else {
                            String sequenceId = (String) res.get(GraphDACParams.node_id.name());
                            req.getContext().get(GraphDACParams.graph_id.name());
                     		SequenceCacheManager.createSequence(graphId, sequenceId, memberIds);
                            Future<Object> response = null;

                            if (null != memberIds && memberIds.size() > 0) {
                                Request dacRequest = new Request(req);
                                dacRequest.setManagerName(GraphDACManagers.DAC_GRAPH_MANAGER);
                                dacRequest.setOperation("createCollection");
                                dacRequest.put(GraphDACParams.collection_id.name(), sequenceId);
                                dacRequest.put(GraphDACParams.relation_type.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
                                dacRequest.put(GraphDACParams.index.name(), SystemProperties.IL_SEQUENCE_INDEX.name());
                                dacRequest.put(GraphDACParams.members.name(), memberIds);
                                dacRouter.tell(dacRequest, manager.getSelf());
                            }
                            manager.returnResponse(response, getParent());
                        }
                    } else {
                        manager.ERROR(GraphEngineErrorCodes.ERR_GRAPH_CREATE_SEQUENCE_UNKNOWN_ERROR.name(),
                                "Failed to create Sequence node", ResponseCode.SERVER_ERROR, getParent());
                    }
                }
            }
        }, ec);
    }

	@Override
	public void removeMembers(Request request) {
		// TODO Auto-generated method stub
		
	}
}
