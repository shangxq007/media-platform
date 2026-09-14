package com.example.platform.providerplugin;
import com.example.platform.extension.api.port.PluginRegistries;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProviderPluginCleanupFailuresTest {
    @Test void startupStopAndUnloadFailuresMustAllRemainActionable() {
        var manager=mock(PluginManager.class);var registry=PluginRegistries.standalone();
        var start=new IllegalStateException("start failure");var stop=new IllegalStateException("stop failure");var unload=new IllegalStateException("unload failure");
        doThrow(start).when(manager).startPlugins();doThrow(stop).when(manager).stopPlugins();doThrow(unload).when(manager).unloadPlugins();
        var host=new ProviderPluginHost(manager,registry);
        var error=assertThrows(ProviderPluginLoadException.class,host::loadAndStart);
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());visit(error,seen);
        verify(manager).stopPlugins();verify(manager).unloadPlugins();
        assertTrue(registry.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
        System.out.println("CLEANUP_PROBE start="+seen.contains(start)+" stop="+seen.contains(stop)+" unload="+seen.contains(unload));
        assertAll(()->assertTrue(seen.contains(start)),()->assertTrue(seen.contains(stop),"stop failure lost"),()->assertTrue(seen.contains(unload)));
    }
    private void visit(Throwable e,Set<Throwable> seen){if(e==null||!seen.add(e))return;visit(e.getCause(),seen);for(var s:e.getSuppressed())visit(s,seen);}

    @Test void individualAndCombinedCleanupFailuresKeepTheStartupCauseAndPermitRetry() {
        for(int mask=0;mask<4;mask++) {
            var manager=mock(PluginManager.class);var registry=PluginRegistries.standalone();
            var start=new IllegalStateException("start");var stop=new IllegalStateException("stop");var unload=new IllegalStateException("unload");
            doThrow(start).when(manager).startPlugins();
            if((mask&1)!=0)doThrow(stop).when(manager).stopPlugins();
            if((mask&2)!=0)doThrow(unload).when(manager).unloadPlugins();
            var host=new ProviderPluginHost(manager,registry);var failure=assertThrows(ProviderPluginLoadException.class,host::loadAndStart);
            assertSame(start,failure.getCause());Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());visit(failure,seen);
            assertTrue(seen.contains(start));assertEquals((mask&1)!=0,seen.contains(stop));assertEquals((mask&2)!=0,seen.contains(unload));
            verify(manager).stopPlugins();verify(manager).unloadPlugins();assertTrue(registry.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
            reset(manager);assertTrue(host.loadAndStart().contributions().isEmpty());host.close();host.close();assertTrue(registry.enumerate().isEmpty());
        }
    }
    @Test void theSameFailureInstanceCannotCauseSelfSuppressionOrDuplicateSuppression() {
        var manager=mock(PluginManager.class);var registry=PluginRegistries.standalone();var shared=new IllegalStateException("same instance");
        doThrow(shared).when(manager).startPlugins();doThrow(shared).when(manager).stopPlugins();doThrow(shared).when(manager).unloadPlugins();
        var failure=assertThrows(ProviderPluginLoadException.class,()->new ProviderPluginHost(manager,registry).loadAndStart());
        assertSame(shared,failure.getCause());assertEquals(0,shared.getSuppressed().length);verify(manager).unloadPlugins();
        reset(manager);var start=new IllegalStateException("different startup");
        doThrow(start).when(manager).startPlugins();doThrow(shared).when(manager).stopPlugins();doThrow(shared).when(manager).unloadPlugins();
        var other=assertThrows(ProviderPluginLoadException.class,()->new ProviderPluginHost(manager,registry).loadAndStart());
        assertSame(start,other.getCause());assertEquals(List.of(shared),Arrays.asList(start.getSuppressed()));
    }
}
