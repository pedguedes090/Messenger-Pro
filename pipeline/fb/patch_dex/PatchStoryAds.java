/**
 * Compatibility entrypoint for the retired name-based story ad patcher.
 * The previous implementation prepended integer returns to arbitrary methods whose
 * names contained "get", "load", or "fetch", which produced invalid or unrelated code.
 * Route callers through the signature- and model-aware ad patcher instead.
 */
public class PatchStoryAds {
    public static void main(String[] args) throws Exception {
        PatchAds.main(args);
    }
}
