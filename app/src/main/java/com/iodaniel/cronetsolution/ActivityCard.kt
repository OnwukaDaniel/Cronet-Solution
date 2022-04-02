package com.iodaniel.cronetsolution

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.iodaniel.cronetsolution.databinding.ActivityCardBinding
import com.iodaniel.cronetsolution.util.UserRegisterListener
import com.iodaniel.cronetsolution.util.Util
import kotlinx.coroutines.*

class ActivityCard : AppCompatActivity(), OnClickListener, UserRegisterListener {
    private val binding by lazy { ActivityCardBinding.inflate(layoutInflater) }
    private var auth = FirebaseAuth.getInstance().currentUser
    private lateinit var userPref: SharedPreferences
    private lateinit var userRegisterListener: UserRegisterListener
    private var cardType: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        userPref = getSharedPreferences(getString(R.string.USER_INFO), Context.MODE_PRIVATE)
        userRegisterListener = this
        binding.cardAddCard.setOnClickListener(this)
        binding.cardBack.setOnClickListener(this)
        binding.cardTypeMaster.setOnClickListener(this)
        binding.cardTypeAmex.setOnClickListener(this)
        binding.cardTypeDisc.setOnClickListener(this)
        binding.cardTypeVisa.setOnClickListener(this)
        getDetails()
    }

    private fun getDetails() {
        val month = userPref.getString("CARD_MONTH", "")
        val year = userPref.getString("CARD_YEAR", "")
        binding.card1.cardName = userPref.getString("CARD_NAME", "")
        binding.card1.cardNumber = userPref.getString("CARD_NUMBER", "")
        binding.card1.expiryDate = "$month/$year"
        binding.card1.type = userPref.getInt("CARD_TYPE", 0)

        binding.cardMonth.setText(userPref.getString("CARD_MONTH", ""))
        binding.cardYear.setText(userPref.getString("CARD_YEAR", ""))
        binding.cardCvv.setText(userPref.getString("CARD_CVV", ""))
        cardType = userPref.getInt("CARD_TYPE", 0)
        binding.card1.type = cardType
    }

    private fun saveDetails() {
        val month = binding.cardMonth.text.toString().trim()
        val year = binding.cardYear.text.toString().trim()

        if (month.length > 2 || year.length > 2) {
            val txt = "Month and Year format is dd/yy"
            Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
            return
        }
        val cardName = binding.card1.cardName
        val cardNumber = binding.card1.cardNumber
        binding.card1.expiryDate = "$month/$year"
        binding.card1.type = cardType

        userPref.edit().putString("CARD_NAME", cardName).apply()
        userPref.edit().putString("CARD_NUMBER", cardNumber).apply()
        userPref.edit().putString("CARD_MONTH", month).apply()
        userPref.edit().putString("CARD_YEAR", year).apply()
        userPref.edit().putString("CARD_CVV", binding.cardCvv.text.trim().toString()).apply()
        userPref.edit().putInt("CARD_TYPE", cardType).apply()

        Snackbar.make(binding.root, "Card Saved", Snackbar.LENGTH_LONG).show()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.card_add_card -> {
                when (auth) {
                    null -> userRegisterListener.unregistered()
                    else -> saveDetails()
                }
            }
            R.id.card_back -> super.onBackPressed()
            R.id.card_type_amex -> cardType = Util.cardTypes[0]
            R.id.card_type_disc -> cardType = Util.cardTypes[1]
            R.id.card_type_master -> cardType = Util.cardTypes[2]
            R.id.card_type_visa -> cardType = Util.cardTypes[3]
        }
        binding.card1.type = cardType
    }

    override fun registeredBuyerOrSeller() {

    }

    override fun unregistered() {
        binding.cardMessageCenter.visibility = View.VISIBLE
        binding.cardMessage.text = "Sign in to add card details"
        binding.cardMessageAction.text = "Sign in"
        binding.cardMessageAction.setOnClickListener {
            val fragmentSignIn = FragmentSignIn()
            fragmentSignIn.baseLayoutToReplace = R.id.card_root
            supportFragmentManager.beginTransaction().addToBackStack("sign in")
                .replace(R.id.card_root, fragmentSignIn).commit()
        }
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                delay(5_000)
                runOnUiThread {
                    binding.cardMessageCenter.visibility = View.GONE
                }
            }
        }
    }

    override fun admin() {
        TODO("Not yet implemented")
    }
}
