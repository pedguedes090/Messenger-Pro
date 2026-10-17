/**
 * The old game-ad patcher is retired. It replaced AudienceNetworkActivity constructors
 * with an unconditional throw and blanked every method named postMessage, which could
 * crash Facebook or suppress unrelated Quicksilver messages.
 */
public class PatchGameAds {
    public static void main(String[] args) {
        throw new UnsupportedOperationException(
                "Unsafe Audience Network/Quicksilver patch disabled; no verified replacement exists. "
                        + "Feed and Reels ads use PatchAds instead.");
    }
}
