/* ============================================================
   StayHub - tailwind-config.js
   Config de Tailwind (CDN) compartida por todas las pantallas.
   Son los tokens de diseño exportados por Stitch (stayhub_design_system/DESIGN.md):
   colores, tipografía, radios y spacing del sistema de diseño.
   Antes este bloque (~100 líneas) estaba pegado y repetido dentro
   del <head> de cada una de las 12 páginas.
   ============================================================ */
tailwind.config = {
    darkMode: "class",
    theme: {
        extend: {
            colors: {
                "surface": "#fbf8fb",
                "surface-dim": "#dbd9dc",
                "surface-bright": "#fbf8fb",
                "surface-container-lowest": "#ffffff",
                "surface-container-low": "#f5f3f5",
                "surface-container": "#efedf0",
                "surface-container-high": "#eae7ea",
                "surface-container-highest": "#e4e2e4",
                "on-surface": "#1b1b1e",
                "on-surface-variant": "#44474d",
                "inverse-surface": "#303032",
                "inverse-on-surface": "#f2f0f3",
                "outline": "#75777e",
                "outline-variant": "#c5c6ce",
                "surface-tint": "#505f7c",
                "primary": "#0f1f39",
                "on-primary": "#ffffff",
                "primary-container": "#25344f",
                "on-primary-container": "#8e9dbd",
                "inverse-primary": "#b7c7e9",
                "secondary": "#4a6078",
                "on-secondary": "#ffffff",
                "secondary-container": "#cae2ff",
                "on-secondary-container": "#4e657d",
                "tertiary": "#2c1c04",
                "on-tertiary": "#ffffff",
                "tertiary-container": "#433116",
                "on-tertiary-container": "#b39875",
                "error": "#ba1a1a",
                "on-error": "#ffffff",
                "error-container": "#ffdad6",
                "on-error-container": "#93000a",
                "primary-fixed": "#d7e3ff",
                "primary-fixed-dim": "#b7c7e9",
                "on-primary-fixed": "#0b1b35",
                "on-primary-fixed-variant": "#384763",
                "secondary-fixed": "#cfe5ff",
                "secondary-fixed-dim": "#b1c9e5",
                "on-secondary-fixed": "#021d32",
                "on-secondary-fixed-variant": "#324960",
                "tertiary-fixed": "#fddeb7",
                "tertiary-fixed-dim": "#dfc29c",
                "on-tertiary-fixed": "#281902",
                "on-tertiary-fixed-variant": "#584326",
                "background": "#fbf8fb",
                "on-background": "#1b1b1e",
                "surface-variant": "#e4e2e4"
            },
            borderRadius: {
                "DEFAULT": "0.25rem",
                "lg": "0.5rem",
                "xl": "0.75rem",
                "full": "9999px"
            },
            spacing: {
                "unit": "4px",
                "xs": "4px",
                "sm": "8px",
                "md": "16px",
                "lg": "24px",
                "xl": "40px",
                "xxl": "64px",
                "container-max": "1440px",
                "gutter": "24px"
            },
            fontFamily: {
                "display-lg": ["Libre Caslon Text"],
                "headline-lg": ["Libre Caslon Text"],
                "headline-lg-mobile": ["Libre Caslon Text"],
                "headline-md": ["Libre Caslon Text"],
                "title-lg": ["Manrope"],
                "body-lg": ["Manrope"],
                "body-md": ["Manrope"],
                "label-md": ["Manrope"]
            },
            fontSize: {
                "display-lg": ["48px", { "lineHeight": "56px", "letterSpacing": "-0.02em", "fontWeight": "700" }],
                "headline-lg": ["32px", { "lineHeight": "40px", "fontWeight": "600" }],
                "headline-lg-mobile": ["24px", { "lineHeight": "32px", "fontWeight": "600" }],
                "headline-md": ["24px", { "lineHeight": "32px", "fontWeight": "600" }],
                "title-lg": ["20px", { "lineHeight": "28px", "fontWeight": "600" }],
                "body-lg": ["16px", { "lineHeight": "24px", "fontWeight": "400" }],
                "body-md": ["14px", { "lineHeight": "20px", "fontWeight": "400" }],
                "label-md": ["12px", { "lineHeight": "16px", "letterSpacing": "0.05em", "fontWeight": "600" }]
            }
        }
    }
};
