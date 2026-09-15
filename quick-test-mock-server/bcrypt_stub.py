"""Stub minimal pour le serveur de TEST uniquement (pas de vrai bcrypt
installé hors-ligne dans ce sandbox). Le vrai backend Java, lui, utilise
un vrai BCrypt (spring-security-crypto)."""
import hashlib

def hashpw(password_bytes):
    return hashlib.sha256(password_bytes).hexdigest().encode()

def checkpw(password_bytes, hashed):
    return hashlib.sha256(password_bytes).hexdigest().encode() == hashed
