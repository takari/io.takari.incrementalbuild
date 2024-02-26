package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class TestBuildContext extends DefaultBuildContext {

    public TestBuildContext(File stateFile, Map<String, Serializable> configuration) {
        this(new FilesystemWorkspace(), stateFile, configuration);
    }

    public TestBuildContext(Workspace workspace, File stateFile, Map<String, Serializable> configuration) {
        super(workspace, stateFile, configuration, null);
    }

    public void commit() throws IOException {
        super.commit(null);
    }

    public Collection<? extends ResourceMetadata<File>> getRegisteredInputs() {
        List<ResourceMetadata<File>> result = new ArrayList<>();
        for (Object resource : state.getResources().keySet()) {
            result.add(new DefaultResourceMetadata<File>(this, state, (File) resource));
        }
        for (Object resource : oldState.getResources().keySet()) {
            if (!state.isResource(resource)) {
                result.add(new DefaultResourceMetadata<File>(this, oldState, (File) resource));
            }
        }
        return result;
    }

    public ResourceStatus getResourceStatus(Object resource) {
        return super.getResourceStatus(resource);
    }

    public Collection<? extends ResourceMetadata<File>> getAssociatedOutputs(DefaultResourceMetadata<File> metadata) {
        Object resource = metadata.getResource();
        return super.getAssociatedOutputs(getState(resource), resource);
    }

    public <T extends Serializable> Serializable setAttribute(DefaultResource<?> resource, String key, T value) {
        return super.setResourceAttribute(resource.getResource(), key, value);
    }

    public <V extends Serializable> V getAttribute(DefaultResourceMetadata<?> resource, String key, Class<V> clazz) {
        return super.getResourceAttribute(getState(resource.getResource()), resource.getResource(), key, clazz);
    }
}
