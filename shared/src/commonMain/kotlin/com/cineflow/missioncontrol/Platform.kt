package com.cineflow.missioncontrol

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform