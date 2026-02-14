package immersive_aircraft.fabric.cobalt.data;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class JsonDataLoaderWrapper implements IdentifiableResourceReloadListener {

    private final Identifier id;
    private final PreparableReloadListener dataLoader;

    public JsonDataLoaderWrapper(Identifier id, PreparableReloadListener dataLoader) {
        this.id = id;
        this.dataLoader = dataLoader;
    }

    @Override
    public Identifier getFabricId() {
        return id;
    }

    @Override
    public @NotNull CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                                    ResourceManager resourceManager,
                                                    ProfilerFiller preparationsProfiler,
                                                    ProfilerFiller reloadProfiler,
                                                    Executor backgroundExecutor,
                                                    Executor gameExecutor) {
        return dataLoader.reload(preparationBarrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
    }
}
