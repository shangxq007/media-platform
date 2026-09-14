package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PluginRegistrationContractTest {
    static PluginDescriptor descriptor(String id,String api,String contract) {
        return new PluginDescriptor(id,"1.0.0",api,"test",
                List.of(new CapabilityDescriptor("media.render",contract,"render","ExecutableTask","ArtifactReference",CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor("ExecutableTask","1","task.Type",List.of(),List.of(),HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),List.of(new PermissionDescriptor("media.execute")),
                PluginDescriptorFixtures.resourceRequirements(),PluginRuntimeRequirement.trustedInProcess(),PluginGuarantee.noneDeclared());
    }
    @Test void registrationAndCapabilityQueriesShareOneImmutableStateAndRetireTogether() {
        PluginRegistrationPort registry=PluginRegistries.standalone();CapabilityRegistryPort capabilities=(CapabilityRegistryPort)registry;
        var descriptor=descriptor("test.plugin","1","1.0");
        var lease=registry.registerRuntime(descriptor);
        assertEquals(List.of(descriptor),registry.enumerate());assertEquals(descriptor,registry.findByPluginIdAndVersion("test.plugin","1.0.0").orElseThrow());
        var entries=capabilities.findCapabilityImplementations(CapabilityId.of("media.render"));assertEquals(1,entries.size());
        assertEquals(ContractVersion.of(1,0),entries.getFirst().contractVersion());
        assertEquals(entries.getFirst(),capabilities.findImplementationById(entries.getFirst().implementationId()).orElseThrow());
        assertThrows(UnsupportedOperationException.class,()->registry.enumerate().clear());
        assertThrows(UnsupportedOperationException.class,entries::clear);
        lease.close();assertTrue(registry.enumerate().isEmpty());assertTrue(capabilities.findCapabilityImplementations(CapabilityId.of("media.render")).isEmpty());
        // Reuse even the same descriptor object; old close must not retire the new entry.
        var next=registry.registerRuntime(descriptor);lease.close();assertEquals(1,registry.enumerate().size());next.close();
    }
    @Test void invalidAndIncompatibleDescriptorsNeverPublishAndRecoveryWorks() {
        var registry=PluginRegistries.standalone();
        var invalid=descriptor("test.plugin","unsupported","1.0");
        var rejected=assertThrows(PluginRegistrationException.class,()->registry.registerRuntime(invalid));
        assertEquals(PluginDiagnosticCode.PLG_003,rejected.issues().getFirst().code());
        assertEquals(rejected.issues(),registry.validate(invalid));assertTrue(registry.enumerate().isEmpty());
        assertThrows(IllegalArgumentException.class,()->registry.registerRuntime(descriptor("test.plugin","1","invalid-contract")));
        assertTrue(registry.findByPluginId("test.plugin").isEmpty());
        assertTrue(registry.findByPluginIdAndVersion("test.plugin","1.0.0").isEmpty());
        assertTrue(((CapabilityRegistryPort)registry).findCapabilityImplementations(CapabilityId.of("media.render")).isEmpty());
        try(var lease=registry.registerRuntime(descriptor("test.plugin","1","1.0"))){assertEquals(1,registry.enumerate().size());}
    }
    @Test void duplicateAndConcurrentRegistrationHaveOneWinnerWithoutPartialCapabilities() throws Exception {
        var registry=PluginRegistries.standalone();var descriptor=descriptor("test.plugin","1","1.0");var start=new CountDownLatch(1);
        try(var threads=Executors.newFixedThreadPool(2)) {
            Callable<Object> attempt=()->{start.await();try{return registry.registerRuntime(descriptor);}catch(PluginRegistrationException e){return e;}};
            var a=threads.submit(attempt);var b=threads.submit(attempt);start.countDown();var results=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(PluginRegistrationPort.Registration.class::isInstance).count());
            var rejected=(PluginRegistrationException)results.stream().filter(PluginRegistrationException.class::isInstance).findFirst().orElseThrow();
            assertEquals(PluginDiagnosticCode.PLG_015,rejected.issues().getFirst().code());
            assertEquals(1,registry.enumerate().size());assertEquals(1,((CapabilityRegistryPort)registry).findCapabilityImplementations(CapabilityId.of("media.render")).size());
            ((PluginRegistrationPort.Registration)results.stream().filter(PluginRegistrationPort.Registration.class::isInstance).findFirst().orElseThrow()).close();
        }
        assertTrue(registry.enumerate().isEmpty());
    }
    @Test void springResolvesBothPublicViewsToTheCanonicalImplementation() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext(
                com.example.platform.extension.app.PluginDescriptorValidator.class,
                com.example.platform.extension.app.PluginHealthRegistry.class,
                com.example.platform.extension.app.PluginRegistryImpl.class)) {
            var registration=context.getBean(PluginRegistrationPort.class);
            assertSame(registration,context.getBean(PluginRegistryPort.class));assertSame(registration,context.getBean(CapabilityRegistryPort.class));
            try(var lease=registration.registerRuntime(descriptor("test.spring","1","1.0"))){assertEquals(1,context.getBean(CapabilityRegistryPort.class).findCapabilityImplementations(CapabilityId.of("media.render")).size());}
        }
    }
}
