import kotlin.collections.listOf
import kotlin.random.Random


fun main() {

    val people: Array<Human> = Array(4) { Human('A', 'A', 0, 0) }

    for (i in 0..2) {
        people[i] = Human(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..4).random())
        people[i].x = 20
        people[i].y = 20
    }

    people[3] = Driver(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..4).random())
    people[3].x = 20
    people[3].y = 20

    val plane = Array(40) { Array(40) { Array(4) { "" } } }

    for (i in 0..3) {
        plane[20][20][i] = "${people[i].name} ${people[i].surname}"
    }

    // Создаем и запускаем потоки для каждого человека
    val threads = mutableListOf<Thread>()

    for (i in 0..3) {
        val thread = Thread {
            val cur = people[i]
            for (j in 0..3) {
                val tempx = cur.x
                val tempy = cur.y

                if (cur is Driver) {
                    val direction = if (Random.nextBoolean()) 'x' else 'y'
                    cur.move(direction, cur.speed * listOf(-1, 1).random())
                } else {
                    cur.move(cur.speed * listOf(-1, 1).random(), cur.speed * listOf(-1, 1).random())
                }

                // Синхронизированное обновление позиции на карте
                synchronized(plane) {
                    plane[tempy][tempx][i] = ""
                    plane[cur.y][cur.x][i] = "${cur.name} ${cur.surname}"
                }
                println("${people[i].name} ${people[i].surname}: (${people[i].x}, ${people[i].y})")
            }
        }
        threads.add(thread)
    }

    // Запускаем все потоки
    threads.forEach { it.start() }

    // Ждем завершения всех потоков
    threads.forEach { it.join() }

    // Вывод итогового положения
    println("ИТОГОВОЕ ПОЛОЖЕНИЕ")
    for (i in 0..3) {
        val type = if (people[i] is Driver) "Водитель" else "Человек"
        println("$type ${people[i].name} ${people[i].surname}: (${people[i].x}, ${people[i].y})")
    }
    println()
}