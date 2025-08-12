package org.net.eu.pool.mica;

// ifversion(>=2100, <<
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentFactory;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
// >>, <<
import dev.onyxstudios.cca.api.v3.component.Component;
import dev.onyxstudios.cca.api.v3.component.ComponentFactory;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry;
// >>)
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;

public class Duck {
    static <C extends Component> void register(WorldComponentFactoryRegistry factories, ComponentKey<? super C> var1, Class<C> var2, ComponentFactory<World, ? extends C> var3) {
        factories.register(var1, var2, var3);
    }
    
    static <T> Long2ObjectMap<T> mkMap() {
        return new Long2ObjectOpenHashMap<T>();
    }
}
