package com.group.smartfood.ui.customer
data class GroupMemberOrder(
    val memberName:String,
    val itemName:String,
    val customization:String,
    val price:Double,
    val isHost:Boolean=false
)
object GroupOrderManager{
    var roomCode:String="GRP-4829"
    var roomName:String="Lunch Squad"
    var restaurantName:String="Placeholder Restaurant"
    var paymentMode:String="Self-pay"
    var cutoffTime:String="Placeholder Time"
    val memberOrders=mutableListOf<GroupMemberOrder>()

    fun initDefaultSampleLobby(){
        memberOrders.clear()
        memberOrders.add(GroupMemberOrder("Member 1 (Host)","Placeholder Item 1","Size L | Option A",3.50,isHost=true))
        memberOrders.add(GroupMemberOrder("Member 2","Placeholder Item 2","Size M | Option B",4.20))
        memberOrders.add(GroupMemberOrder("Member 3","Placeholder Item 3","Standard | Option C",2.80))
    }
    fun addPlaceholderMember(){
        val nextIndex=memberOrders.size+1
        val samplePrices=listOf(3.50,4.20,2.80,3.90,4.50)
        val price=samplePrices[(nextIndex-1)%samplePrices.size]
        memberOrders.add(
            GroupMemberOrder(
                memberName="Member $nextIndex",
                itemName="Placeholder Item $nextIndex",
                customization="Standard | Default Option",
                price=price
            )
        )
    }
    fun getTotalAmount():Double=memberOrders.sumOf{it.price}
}