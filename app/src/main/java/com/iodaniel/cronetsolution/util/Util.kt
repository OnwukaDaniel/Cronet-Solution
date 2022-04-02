package com.iodaniel.cronetsolution.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.iodaniel.cronetsolution.R
import com.vinaygaba.creditcardview.CardType
import java.io.ByteArrayOutputStream

object Util {

    val cardTypes = arrayListOf(
        CardType.AMERICAN_EXPRESS,
        CardType.DISCOVER,
        CardType.MASTERCARD,
        CardType.VISA)

    val cardTypesImages = arrayListOf(
        R.drawable.amex,
        R.drawable.discover,
        R.drawable.mastercard,
        R.drawable.visa)

    fun fromOutPutStreamString(stream: String): Bitmap? {
        val decodedByteArray: ByteArray = Base64.decode(stream, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(decodedByteArray, 0, stream.length)
    }

    fun bitmapToString(bitmap: Bitmap): String? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun scaleBitmap(pathInUri: Uri, applicationContext: Context): Bitmap {
        val contentResolver = applicationContext.contentResolver
        val inputStream = contentResolver.openInputStream(pathInUri)
        val bm = BitmapFactory.decodeStream(inputStream)
        val inh = (bm.height * (512.0 / bm.width)).toInt()
        return Bitmap.createScaledBitmap(bm, 512, inh, true)
    }
}

object Keyboard{
    fun Fragment.hideKeyboard(){
        view?.let { activity?.hideKeyboard(it) }
    }
    fun Activity.hideKeyboard(){
        hideKeyboard(currentFocus?: View(this))
    }
    private fun Context.hideKeyboard(view: View){
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

class UserTypeData(
    var id: Int = 0,
    var email: String = "",
    var openOrders: Int = 0,
    var uploads: Int = 0,
    var userType: String = "Buyer",
    var imageUri: String = "",
    var userUID: String = "",
    var dateJoined: String = "",
    var userName: String = "",
)

object UserTypes {
    const val BUYER = "Buyer"
    const val SELLER = "Seller"
    const val ADMIN = "Admin"
}

object RoundCornerBackground{
    const val UNSELECTED = R.drawable.round_corner
    const val SELECTED = R.drawable.round_corner_selected
}

interface UserRegisterListener {
    fun registeredBuyerOrSeller()
    fun unregistered()
    fun admin()
}


interface UserTypeListener {
    fun admin()
    fun seller()
    fun buyer()
}