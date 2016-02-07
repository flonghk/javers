package org.javers.core.graph;

import org.javers.common.collections.EnumerableFunction;
import org.javers.core.metamodel.object.Cdo;
import org.javers.core.metamodel.object.OwnerContext;
import org.javers.core.metamodel.property.Property;
import org.javers.core.metamodel.type.ContainerType;
import org.javers.core.metamodel.type.EnumerableType;
import org.javers.core.metamodel.type.ManagedType;
import org.javers.core.metamodel.type.MapType;
import org.javers.core.metamodel.type.TypeMapper;

/**
 * @author bartosz walacik
 */
class EdgeBuilder {
    private final TypeMapper typeMapper;
    private final NodeReuser nodeReuser;
    private final CdoFactory cdoFactory;

    EdgeBuilder(TypeMapper typeMapper, NodeReuser nodeReuser, CdoFactory cdoFactory) {
        this.typeMapper = typeMapper;
        this.nodeReuser = nodeReuser;
        this.cdoFactory = cdoFactory;
    }

    String graphType(){
        return cdoFactory.typeDesc();
    }

    /**
     * @return node stub, could be redundant so check reuse context
     */
    SingleEdge buildSingleEdge(ObjectNode node, Property singleRef) {
        Object rawReference = node.getPropertyValue(singleRef);

        Cdo cdo = asCdo(rawReference, createOwnerContext(node, singleRef), singleRef.hasShallowReferenceAnn());
        ObjectNode referencedNode = buildNodeStubOrReuse(cdo);

        return new SingleEdge(singleRef, referencedNode);
    }

    Cdo asCdo(Object target, OwnerContext owner, boolean isShallowReference) {
        return cdoFactory.create(target, owner, isShallowReference);
    }

    private OwnerContext createOwnerContext(ObjectNode parentNode, Property property) {
        return new OwnerContext(parentNode.getGlobalId(), property.getName());
    }

    MultiEdge createMultiEdge(Property containerProperty, EnumerableType enumerableType, ObjectNode node) {
        MultiEdge multiEdge = new MultiEdge(containerProperty);
        OwnerContext owner = createOwnerContext(node, containerProperty);

        Object container = node.getPropertyValue(containerProperty);

        EnumerableFunction edgeBuilder = null;
        if (enumerableType instanceof MapType){
            edgeBuilder = new MultiEdgeMapBuilderFunction(multiEdge);
        } else if (enumerableType instanceof ContainerType){
            edgeBuilder = new MultiEdgeContainerBuilderFunction(multiEdge);
        }
        enumerableType.map(container, edgeBuilder, owner);

        return multiEdge;
    }

    private class MultiEdgeContainerBuilderFunction implements EnumerableFunction {
        final MultiEdge multiEdge;

        public MultiEdgeContainerBuilderFunction(MultiEdge multiEdge) {
            this.multiEdge = multiEdge;
        }

        @Override
        public Object apply(Object input, OwnerContext enumerationAwareOwnerContext) {
            if (!isManagedPosition(input)){
                return input;
            }
            ObjectNode objectNode = buildNodeStubOrReuse(asCdo(input, enumerationAwareOwnerContext,false));
            multiEdge.addReferenceNode(objectNode);
            return input;
        }

        boolean isManagedPosition(Object input){
            return true;
        }
    }

    private class MultiEdgeMapBuilderFunction extends MultiEdgeContainerBuilderFunction {
        public MultiEdgeMapBuilderFunction(MultiEdge multiEdge) {
            super(multiEdge);
        }

        boolean isManagedPosition(Object input){
            return typeMapper.getJaversType(input.getClass()) instanceof ManagedType;
        }
    }

    private ObjectNode buildNodeStubOrReuse(Cdo cdo){
        if (nodeReuser.isReusable(cdo)){
            return nodeReuser.getForReuse(cdo);
        }
        else {
            return buildNodeStub(cdo);
        }
    }

    ObjectNode buildNodeStub(Cdo cdo){
        ObjectNode newStub = new ObjectNode(cdo);
        nodeReuser.enqueueStub(newStub);
        return newStub;
    }
}
