package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.*;
import com.example.platform.extension.api.port.*;
import com.example.platform.extension.domain.*;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pf4j.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production host + real Extension registry, with controlled PF4J failure windows. */
class ProviderPluginRegistrationLifecycleTest {
    @Test void disableUnloadAndCloseRetireDescriptorsAndCapabilitiesWithTheCatalog() {
        var registry=PluginRegistries.standalone();var manager=manager("test.alpha","test.beta");
        var host=new ProviderPluginHost(manager,registry);assertEquals(2,host.loadAndStart().contributions().size());assertEquals(2,registry.enumerate().size());
        when(manager.disablePlugin("test.alpha")).thenReturn(true);assertTrue(host.disable("test.alpha"));
        assertTrue(registry.findByPluginId("test.alpha").isEmpty());assertEquals(1,host.catalog().contributions().size());
        when(manager.unloadPlugin("test.beta")).thenReturn(true);assertTrue(host.unload("test.beta"));
        assertTrue(registry.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
        assertTrue(((CapabilityRegistryPort)registry).findCapabilityImplementations(CapabilityId.of("media.render")).isEmpty());host.close();
    }
    @Test void registrationRaceRollsBackOnlyThisHostsEntriesAndPreservesTheOtherWinner() {
        var store=PluginRegistries.standalone();var port=mock(PluginRegistrationPort.class);
        when(port.validate(any())).thenAnswer(i->store.validate(i.getArgument(0)));
        when(port.findByPluginId(any())).thenAnswer(i->store.findByPluginId(i.getArgument(0)));
        AtomicReference<PluginRegistrationPort.Registration> other=new AtomicReference<>();
        when(port.registerRuntime(any())).thenAnswer(i->{
            com.example.platform.extension.domain.PluginDescriptor descriptor=i.getArgument(0);
            if(descriptor.pluginId().equals("test.beta"))other.set(store.registerRuntime(descriptor));
            return store.registerRuntime(descriptor);
        });
        var manager=manager("test.alpha","test.beta");var host=new ProviderPluginHost(manager,port);
        var rejected=assertThrows(ProviderPluginLoadException.class,host::loadAndStart);
        assertTrue(rejected.getMessage().contains("INVALID_PLATFORM_PLUGIN_DESCRIPTOR"));assertTrue(host.catalog().contributions().isEmpty());
        assertTrue(store.findByPluginId("test.alpha").isEmpty());assertTrue(store.findByPluginId("test.beta").isPresent());
        assertEquals(1,((CapabilityRegistryPort)store).findCapabilityImplementations(CapabilityId.of("media.render")).size());
        verify(manager).stopPlugins();verify(manager).unloadPlugins();host.close();assertTrue(store.findByPluginId("test.beta").isPresent());other.get().close();
    }
    @Test void startupFailurePreservesOriginalErrorWhenCleanupAlsoFails() {
        var store=PluginRegistries.standalone();var manager=mock(PluginManager.class);
        var startup=new IllegalStateException("startup");var cleanup=new IllegalStateException("cleanup");
        doThrow(startup).when(manager).startPlugins();doThrow(cleanup).when(manager).stopPlugins();
        var host=new ProviderPluginHost(manager,store);var failed=assertThrows(ProviderPluginLoadException.class,host::loadAndStart);
        assertSame(startup,failed.getCause());assertEquals(List.of(cleanup),Arrays.asList(startup.getSuppressed()));
        assertTrue(store.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());verify(manager).unloadPlugins();
    }
    @Test void failedPf4jStateRejectsWithoutAdvertisementThenCanRecover() {
        var store=PluginRegistries.standalone();var manager=manager("test.alpha");
        when(manager.getPlugins().getFirst().getPluginState()).thenReturn(PluginState.FAILED);
        var host=new ProviderPluginHost(manager,store);assertThrows(ProviderPluginLoadException.class,host::loadAndStart);
        assertTrue(store.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
        when(manager.getPlugins().getFirst().getPluginState()).thenReturn(PluginState.STARTED);
        assertEquals(1,host.loadAndStart().contributions().size());assertEquals(1,store.enumerate().size());host.close();assertTrue(store.enumerate().isEmpty());
    }
    @Test void closeAttemptsEveryStopAndUnloadAndRetiresEvenWhenOneStopFails() {
        var store=PluginRegistries.standalone();var manager=manager("test.alpha","test.beta");var host=new ProviderPluginHost(manager,store);host.loadAndStart();
        var failure=new IllegalStateException("cannot stop alpha");doThrow(failure).when(manager).stopPlugin("test.alpha");
        assertSame(failure,assertThrows(IllegalStateException.class,host::close));
        verify(manager).stopPlugin("test.beta");verify(manager).unloadPlugin("test.alpha");verify(manager).unloadPlugin("test.beta");
        assertTrue(store.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
    }
    @Test void failedDisableStillRevokesAvailabilityRatherThanLeavingStaleAdvertisement() {
        var store=PluginRegistries.standalone();var manager=manager("test.alpha");var host=new ProviderPluginHost(manager,store);host.loadAndStart();
        var failure=new IllegalStateException("stop failed");doThrow(failure).when(manager).stopPlugin("test.alpha");
        assertSame(failure,assertThrows(IllegalStateException.class,()->host.disable("test.alpha")));
        assertTrue(store.enumerate().isEmpty());assertTrue(host.catalog().contributions().isEmpty());
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private PluginManager manager(String... ids) {
        var manager=mock(PluginManager.class);List<ProviderPluginContribution> contributions=new ArrayList<>();List<PluginWrapper> wrappers=new ArrayList<>();
        for(String id:ids){
            var contribution=mock(ProviderPluginContribution.class);var pin=new ProviderBindingPin(ProviderId.of(id),ProviderImplementationId.of(id+".impl"),ProviderVersion.of("1.0.0"),ProviderExecutionContractVersion.of(1,0),ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1,0)),List.of());
            var providerId=pin.providerId();var implementationId=pin.providerImplementationId();var version=pin.providerVersion();var contract=pin.providerExecutionContractVersion();var profileRef=pin.providerCapabilityProfileVersionOrDigest();
            var provider=mock(ProviderDescriptor.class);when(provider.providerId()).thenReturn(providerId);when(provider.providerImplementationId()).thenReturn(implementationId);when(provider.providerVersion()).thenReturn(version);
            var execution=mock(ProviderExecutionContract.class);when(execution.contractVersion()).thenReturn(contract);
            var profile=mock(ProviderCapabilityProfile.class);when(profile.reference()).thenReturn(profileRef);
            var support=mock(WorkerRuntimeSupportRequirement.class);when(support.providerBindingPin()).thenReturn(pin);
            when(contribution.pluginId()).thenReturn(id);when(contribution.pluginVersion()).thenReturn("1.0.0");when(contribution.pluginDescriptor()).thenReturn(descriptor(id));
            when(contribution.providerBindingPin()).thenReturn(pin);when(contribution.providerDescriptor()).thenReturn(provider);when(contribution.providerExecutionContract()).thenReturn(execution);when(contribution.providerCapabilityProfile()).thenReturn(profile);when(contribution.workerRuntimeSupportRequirement()).thenReturn(support);
            contributions.add(contribution);
            var wrapper=mock(PluginWrapper.class);var descriptor=mock(org.pf4j.PluginDescriptor.class);when(descriptor.getVersion()).thenReturn("1.0.0");
            when(wrapper.getPluginId()).thenReturn(id);when(wrapper.getDescriptor()).thenReturn(descriptor);when(wrapper.getPluginState()).thenReturn(PluginState.STARTED);
            wrappers.add(wrapper);when(manager.getPlugin(id)).thenReturn(wrapper);when(manager.getExtensions(ProviderPluginContribution.class,id)).thenReturn(List.of(contribution));
        }
        when(manager.getExtensions(ProviderPluginContribution.class)).thenReturn(contributions);
        when(manager.getPlugins()).thenReturn(wrappers);when(manager.getStartedPlugins()).thenReturn(wrappers);
        var next=new java.util.concurrent.atomic.AtomicInteger();when(manager.whichPlugin(any())).thenAnswer(i->wrappers.get(next.getAndIncrement()%wrappers.size()));
        return manager;
    }
    private com.example.platform.extension.domain.PluginDescriptor descriptor(String id) {
        return new com.example.platform.extension.domain.PluginDescriptor(id,"1.0.0","1","test",
                List.of(new CapabilityDescriptor("media.render","1.0","render","ExecutableTask","ArtifactReference",CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor("ExecutableTask","1","task.Type",List.of(),List.of(),HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),List.of(new PermissionDescriptor("media.execute")),new ResourceRequirement(1,256,50,0,1024,1024,1000,false,1024,false,1000),
                PluginRuntimeRequirement.trustedInProcess(),PluginGuarantee.noneDeclared());
    }
}
