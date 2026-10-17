/* Todos los templates HTML dinámicos pasan por DOMPurify local antes de insertarse. */
const StayHubDOM = Object.freeze({
    html(value) {
        var markup = String(value == null ? "" : value);
        var config = {
            USE_PROFILES: { html: true },
            FORBID_TAGS: ["style", "iframe", "object", "embed", "form"],
            FORBID_ATTR: ["style", "srcdoc"],
            ALLOW_UNKNOWN_PROTOCOLS: false,
        };
        // El parser HTML descarta tr/td sueltos fuera de una tabla. Sanitizar
        // en su contexto conserva las celdas sin permitir atributos inseguros.
        if (/^\s*<tr[\s>]/i.test(markup)) {
            return DOMPurify.sanitize('<table><tbody>' + markup + '</tbody></table>',
                Object.assign({}, config, { RETURN_DOM: true })).querySelector('tbody').innerHTML;
        }
        if (/^\s*<(td|th)[\s>]/i.test(markup)) {
            return DOMPurify.sanitize('<table><tbody><tr>' + markup + '</tr></tbody></table>',
                Object.assign({}, config, { RETURN_DOM: true })).querySelector('tr').innerHTML;
        }
        return DOMPurify.sanitize(markup, config);
    },
    text(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function(character) {
            return {"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[character];
        });
    },
});
