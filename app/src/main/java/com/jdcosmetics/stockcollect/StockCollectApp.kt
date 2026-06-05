package com.jdcosmetics.stockcollect

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'application.
 * @HiltAndroidApp déclenche la génération du code Hilt.
 */
@HiltAndroidApp
class StockCollectApp : Application()
