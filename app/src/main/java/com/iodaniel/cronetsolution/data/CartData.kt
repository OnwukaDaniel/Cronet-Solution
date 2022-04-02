package com.iodaniel.cronetsolution.data

class CartData(
    var id: Int = 0,
    var productName: String = "",
    var productDescription: String = "",
    var productPrice: String = "",
    var orderQuantity: String = "",
    var productQuantity: String = "",
    var productDiscount: String = "",
    var productSeo: String = "",
    var productImageUrl: String = "",
    var productSection: String = "",

    var dateCreated: Long = 0L,
    var dateAddedToCart: String = "",
    var pushId: String = "",
    var sellerUid: String = "",
    var buyerId: String = "",
    var included: Boolean = false,
)