package com.iodaniel.cronetsolution

import androidx.lifecycle.LiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

class RealTimeDatabaseLiveData(private val ref: DatabaseReference): LiveData<DataSnapshot>(){
    private var listener = CartListener()
    override fun onActive() {
        super.onActive()
        ref.addValueEventListener(listener)
    }

    override fun onInactive() {
        super.onInactive()
        ref.removeEventListener(listener)
    }
    inner class CartListener: ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            value = snapshot
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}