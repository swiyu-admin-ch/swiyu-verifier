package ch.admin.bj.swiyu.verifier.common.profile;

import lombok.experimental.UtilityClass;

/**
 * Central place for Swiss Profile version indications.
 *
 * <p>Per "Versioning Indications in Swiss Profile":
 * <ul>
 *   <li>In JWTs the versioning attribute {@code profile_version} goes into the JWT header.</li>
 *   <li>In regular JSON bodies the versioning attribute {@code profile_version} goes into the JSON body.</li>
 * </ul>
 */
@UtilityClass
public class SwissProfileVersions {

    /**
     * Swiss Profile version indication.
     *
     * Must be present as "profile_version" parameter in the JWS header of the
     * JWT-Secured Authorization Request (JAR) for swiss-profile-verification.
     */
    public static final String VERIFICATION_PROFILE_VERSION = "swiss-profile-verification:1.0.0";

    /**
     * JSON/JWT-header parameter name.
     */
    public static final String PROFILE_VERSION_PARAM = "profile_version";
}