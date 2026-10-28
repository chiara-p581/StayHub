-- Usuarios exclusivos del entorno Docker local. No usar estas credenciales fuera de la demo.
INSERT INTO usuario (email, password_hash, nombre, apellido, rol, version)
VALUES
    ('admin@stayhub.local', 'pbkdf2-sha256$310000$U3RheUh1YkRlbW9TYWx0IQ==$+yE9g2vlck7pqvZQW8Yt1ILuxJYHxYIvn0HlotroQLg=', 'Admin', 'StayHub', 'ADMIN', 0),
    ('huesped@stayhub.local', 'pbkdf2-sha256$310000$U3RheUh1YkRlbW9TYWx0IQ==$+yE9g2vlck7pqvZQW8Yt1ILuxJYHxYIvn0HlotroQLg=', 'Huésped', 'Demo', 'HUESPED', 0)
ON CONFLICT (email) DO NOTHING;
