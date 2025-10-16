fun main() {

    val people: Array<Human?> = Array(17) { null }

    val surnames = arrayOf(
        "Иванов", "Петров", "Васильев", "Андреев",
        "Смирнов", "Кузнецов", "Попов", "Соколов",
        "Лебедев", "Козлов", "Новиков", "Морозов",
        "Волков", "Соловьев", "Зайцев", "Павлов", "Романов"
    )

    val names = arrayOf(
        "Иван", "Петр", "Василий", "Андрей", "Алексей", "Дмитрий", "Сергей", "Михаил",
        "Мария", "Анна", "Елена", "Ольга", "Наталья", "Ирина", "Светлана", "Татьяна",
        "Екатерина"
    )

    for(i in 0..16){
        people[i] = Human(names[i], surnames[i], (1..10).random(), (1..10).random())
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