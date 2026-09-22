package net.vanbaelinghem.skylanders.api;

/**
 * A placement the portal refuses, carrying a {@link Notice} rather than a sentence.
 *
 * <p>Same reasoning as everywhere else on this API: the server names the situation, the
 * interface — which alone knows what language it is being read in — turns it into a sentence.
 */
public class PortalRejectedException extends RuntimeException {

    private final transient Notice notice;

    public PortalRejectedException(Notice notice) {
        super(notice.code());
        this.notice = notice;
    }

    public Notice notice() {
        return notice;
    }
}
