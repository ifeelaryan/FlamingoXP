package com.example.api_interface

import androidx.annotation.DrawableRes

data class Spice(
    val id: Int,
    val name: String,
    val description: String = "",
    val category: String = "",
    val price: Double,
    @DrawableRes val imageRes: Int,
    val deliveryTime: String = "",
    val weight: String = "100g",
    val rating: Double = 4.5,
    val discount: Int = 0
)

val allSpices = listOf(
    Spice(1, "Turmeric Powder", "Organic Turmeric", "Ground", 120.0, R.drawable.turmeric, "2-4 days", "100g", 4.8, 10),
    Spice(2, "Red Chilli Powder", "Hot Red Chilli", "Ground", 150.0, R.drawable.turmeric, "3-5 days", "100g", 4.7, 15),
    Spice(3, "Cinnamon (Dalchini)", "Sweet Cinnamon", "Whole", 200.0, R.drawable.turmeric, "2-4 days", "50g", 4.5, 5),
    Spice(4, "Cloves (Laung)", "Aromatic Cloves", "Whole", 180.0,
        R.drawable.red_chilli, "3-5 days", "100g", 4.6, 20),
    Spice(5, "Black Pepper", "Spicy Black Pepper", "Whole", 220.0, R.drawable.black_pepper, "2-4 days", "100g", 4.9, 12),
    Spice(6, "Cumin Seeds", "Pure Cumin", "Whole", 120.0, R.drawable.turmeric, "2-3 days", "100g", 4.4, 8),
    Spice(7, "Coriander Powder", "Fresh Coriander", "Ground", 90.0, R.drawable.turmeric, "2-3 days", "100g", 4.3, 5),
    Spice(8, "Star Anise", "Whole Star Anise", "Whole", 300.0, R.drawable.turmeric, "4-6 days", "50g", 4.5, 10),
    Spice(9, "Saffron", "Kashmiri Saffron", "Organic", 999.0, R.drawable.turmeric, "5-7 days", "1g", 5.0, 15)
)
