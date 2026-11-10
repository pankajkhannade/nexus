package dev.pankajkhannade.nexus

/** Phone-book fixtures for tests. [granted] = READ_CONTACTS allowed. */
class FakeContacts(var granted: Boolean = true, var contacts: List<PhoneContact> = PHONE_BOOK) : ContactsSource {
    var loads = 0
        private set
    override fun hasPermission() = granted
    override fun load(): List<PhoneContact> { loads++; return if (granted) contacts else emptyList() }
}

val PHONE_BOOK = listOf(
    PhoneContact("1", "Sunil Patil", listOf("98220 11111")),
    PhoneContact("2", "Ramesh Kaka", listOf("98220 22222")),
    PhoneContact("3", "Ramesh Joshi", listOf("98220 33333")),
    PhoneContact("4", "Rahul Joshi", listOf("+91 98220 44441")),
    PhoneContact("5", "Rahul Kulkarni", listOf("098220 44442")),
    PhoneContact("6", "Anil Sharma", listOf("98220 55555")),
    PhoneContact("7", "राजेश देशमुख", listOf("98220 66666")),
    PhoneContact("8", "Sanjay Bhosale", listOf("98220 77777")),
    PhoneContact("9", "Dr Kelkar", listOf("020 2543 1234")),
    PhoneContact("10", "Mahesh Dada", listOf("98220 88888")),
    PhoneContact("11", "Priya Mehta", listOf("98220 99991")),
    PhoneContact("12", "Priya Shah", listOf("98220 99992"), starred = true),
    PhoneContact("13", "Meena Mavshi", listOf("98220 12121", "020 2444 5555")),
    PhoneContact("14", "Vikas Gym", listOf("12")),
    PhoneContact("15", "Kavita", listOf("98220 13131")),
    PhoneContact("16", "Kavita Old", listOf("+91 98220 13131")),
)
