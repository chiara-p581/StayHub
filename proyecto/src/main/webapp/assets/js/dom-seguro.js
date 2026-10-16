/* Todos los templates HTML dinámicos pasan por DOMPurify local antes de insertarse. */
const StayHubDOM = Object.freeze({
    html(value) {
        return DOMPurify.sanitize(String(value == null ? "" : value), {
            USE_PROFILES: { html: true },
            FORBID_TAGS: ["style", "iframe", "object", "embed", "form"],
            FORBID_ATTR: ["style", "srcdoc"],
            ALLOW_UNKNOWN_PROTOCOLS: false,
        });
    },
    text(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function(character) {
            return {"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[character];
        });
    },
});
