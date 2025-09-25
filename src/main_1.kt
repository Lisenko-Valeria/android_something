fun main() {

    val people: Array<Human?> = Array(17) { null }

    for(i in 0..16){
        people[i] = Human(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..10).random())
    }

    for(j in 0..10){
        for(i in 0..16){
            val cur = people[i]!! //утв не null
            cur.move(cur.speed*listOf(-1,1).random(), cur.speed*listOf(-1,1).random())
        }
    }

    for(i in 0..16){
        val cur = people[i]!!
        cur.prr()
    }
}