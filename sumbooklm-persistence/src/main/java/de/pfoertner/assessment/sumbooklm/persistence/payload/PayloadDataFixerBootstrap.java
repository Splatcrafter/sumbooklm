package de.pfoertner.assessment.sumbooklm.persistence.payload;

import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.persistence.user.UserAccountPayload;
import de.splatgames.aether.datafixers.api.DataVersion;
import de.splatgames.aether.datafixers.api.bootstrap.DataFixerBootstrap;
import de.splatgames.aether.datafixers.api.fix.FixRegistrar;
import de.splatgames.aether.datafixers.api.schema.Schema;
import de.splatgames.aether.datafixers.api.schema.SchemaRegistry;
import de.splatgames.aether.datafixers.api.type.SimpleType;
import de.splatgames.aether.datafixers.api.type.TypeRegistry;
import de.splatgames.aether.datafixers.core.type.SimpleTypeRegistry;
import org.springframework.stereotype.Component;

/**
 * Declares the payload schemas of the application and the fixes that migrate between them.
 *
 * <h2>Discovery</h2>
 * The Aether Datafixers starter picks this bean up, reads {@link #CURRENT_VERSION} by reflection to
 * learn which version the running application writes, and assembles the executable data fixer from
 * the registrations below. The assembled fixer is published as a bean and is what
 * {@link PayloadCodec} encodes, migrates and decodes with.
 *
 * <h2>Adding a Version</h2>
 * A payload change adds a constant to {@link PayloadSchemaVersion}, registers a schema for the new
 * version in {@link #registerSchemas(SchemaRegistry)} with the codecs of that version, and registers
 * the fixes that carry data from the previous version to it in
 * {@link #registerFixes(FixRegistrar)}. Schemas of earlier versions are retained, because they are
 * what stored data is read with before it is migrated.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class PayloadDataFixerBootstrap implements DataFixerBootstrap {

    /**
     * Payload schema version the running application writes. The Aether Datafixers starter resolves
     * this constant by name, so it has to stay public, static and of this exact type.
     */
    public static final DataVersion CURRENT_VERSION = new DataVersion(PayloadSchemaVersion.CURRENT);

    /**
     * Creates the bootstrap. The instance is created by the container and holds no state.
     */
    public PayloadDataFixerBootstrap() {
    }

    /**
     * Registers one schema per payload schema version.
     *
     * @param schemas registry the schemas are contributed to
     */
    @Override
    public void registerSchemas(final SchemaRegistry schemas) {
        final TypeRegistry initialTypes = new SimpleTypeRegistry();
        initialTypes.register(new SimpleType<>(PayloadTypes.USER_ACCOUNT, UserAccountPayload.CODEC));
        schemas.register(new Schema(new DataVersion(PayloadSchemaVersion.V1_0_0), initialTypes));
    }

    /**
     * Registers the data fixes applied when a stored payload is older than {@link #CURRENT_VERSION}.
     *
     * <p>No fix is registered yet: {@code 1.0.0} is the initial payload schema version, so there is
     * no earlier version any stored payload could carry.
     *
     * @param fixes registrar the fixes are contributed to
     */
    @Override
    public void registerFixes(final FixRegistrar fixes) {
    }
}
