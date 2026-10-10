package com.example.reto2.data.model

data class User(val uid: String, val email: String)

// Error con mensaje listo para mostrar en pantalla.
class AuthException(message: String) : Exception(message)