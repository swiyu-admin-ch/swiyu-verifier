package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class DcqlService {

    private static List<Object> selectClaim(SdJwt sdJwt, DcqlClaim requestedClaim) {
        var selected = new DcqlPathSelection(sdJwt.getClaims().getClaims());
        List<Object> requestedPath = requestedClaim.getPath();
        for (Object path : requestedPath) {
            switch (path) {
                case null -> selected = selected.selectAll();
                case Number number -> selected = selected.selectElement(number.intValue());
                case String s -> selected = selected.selectElement(s);
                default ->
                        throw new IllegalArgumentException("Illegal request path type; was %s".formatted(path.getClass()));
            }
        }
        return selected.selected;
    }

    /**
     * Validate if the requestedClaims are present in the jwt
     * Throws Illegal Argument Exception if something is wrong with the presented sd jwt
     * @throws IllegalArgumentException if not all requested claims are present in the sd jwt's claims
     */
    public static void containsRequestedFields(SdJwt sdJwt, List<DcqlClaim> requestedClaims) {
        if (CollectionUtils.isEmpty(requestedClaims)) {
            return;
        }
        // Collect all presented claims
        for (DcqlClaim requestedClaim : requestedClaims) {
            var claims = selectClaim(sdJwt, requestedClaim);

            var requestedValues = requestedClaim.getValues();
            if (requestedValues != null && !requestedValues.containsAll(claims)) {
                throw new IllegalArgumentException("Not all requested claim values are satisfied");
            }
        }
    }

    public static List<SdJwt> filterByVct(List<SdJwt> sdJwts, DcqlCredentialMeta credentialMeta) {
        if (credentialMeta == null) {
            return sdJwts;
        }
        var acceptedVcts = credentialMeta.getVctValues();
        if (CollectionUtils.isEmpty(acceptedVcts)) {
            return sdJwts;
        }
        // TODO Handle VCT extends according to https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#I-D.ietf-oauth-sd-jwt-vc or decide to not support it in swiss profile
        return sdJwts.stream().filter(presentation -> acceptedVcts.contains(presentation.getClaims().getClaims().get("vct"))).toList();
    }

    /**
     * Used for processing of Dcql Paths
     * <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-7.1.1">OID4VP Processing DCQL</a>
     */
    private record DcqlPathSelection(List<Object> selected) {
        /**
         * Select the root element of the Credential, i.e., the top-level JSON object.
         */
        public DcqlPathSelection(Object root) {
            this(List.of(root));
        }

        /**
         * If the set of elements currently selected is empty, abort processing and return an error.
         */
        private DcqlPathSelection {
            if (CollectionUtils.isEmpty(selected)) {
                throw new IllegalArgumentException("Requested DCQL path could not be found");
            }
        }

        /**
         * If the component is a string, select the element in the respective key in the currently selected element(s).
         * If any of the currently selected element(s) is not an object, abort processing and return an error.
         * If the key does not exist in any element currently selected, remove that element from the selection
         */
        public DcqlPathSelection selectElement(String key) {
            List<Object> newSelection = new LinkedList<>();
            for (Object currentSelected : selected) {
                if (!(currentSelected instanceof Map)) {
                    throw new IllegalArgumentException("Illegal claim type for selection %s - found %s instead of Json Object".formatted(key, currentSelected.getClass()));
                }
                var newElement = ((Map<?, ?>) currentSelected).get(key);
                if (newElement != null) {
                    newSelection.add(newElement);
                }
            }
            return new DcqlPathSelection(newSelection);
        }

        /**
         * If the component is a non-negative integer, select the element at the respective index in the currently selected array(s).
         * If any of the currently selected element(s) is not an array, abort processing and return an error.
         * If the index does not exist in a selected array, remove that array from the selection.
         */
        public DcqlPathSelection selectElement(int index) {
            List<Object> newSelection = new LinkedList<>();
            for (Object currentSelected : selected) {
                if (!(currentSelected instanceof List)) {
                    throw new IllegalArgumentException("Illegal claim type for selection %s - found %s instead of Json Array".formatted(index, currentSelected.getClass()));
                }
                if (index < ((List<?>) currentSelected).size()) {
                    newSelection.add(((List<?>) currentSelected).get(index));
                }
            }
            return new DcqlPathSelection(newSelection);
        }

        /**
         * If the component is null, select all elements of the currently selected array(s).
         * If any of the currently selected element(s) is not an array, abort processing and return an error.
         */
        public DcqlPathSelection selectAll() {
            List<Object> newSelection = new LinkedList<>();
            for (Object currentSelected : selected) {
                if (!(currentSelected instanceof List)) {
                    throw new IllegalArgumentException("Illegal claim type for selecting all array elements - found %s instead of Json Array".formatted(currentSelected.getClass()));
                }
                newSelection.addAll((List<?>) currentSelected);
            }
            // unpack array to selected
            return new DcqlPathSelection(newSelection);
        }
    }
}
