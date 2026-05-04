package no.nav.openinghours.controller

/*
@WebMvcTest(RuleController::class)
class OpeningHoursControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: RuleService

    @Test
    fun `test get opening hours by id`() {
        val id = UUID.randomUUID()
        val openingHours = Rule(id, "Test Rule Name", "??.??.???? ? 1-7 00:00-00:00", "Test Header", "Text", false)

        Mockito.`when`(service.get(id)).thenReturn(openingHours)

        mockMvc.perform(get("/api/openinghours/rule/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Test Rule Name"))
            .andExpect(jsonPath("$.rule").value("??.??.???? ? 1-7 00:00-00:00"))
    }

    @Test
    fun `test upsert opening hours`() {
        val openingHours = Rule(UUID.randomUUID(), "Test Name", "Test Rule", "Header", "Text", false)

        Mockito.`when`(service.upsert("Test Name", "Test Rule")).thenReturn(openingHours)

        mockMvc.perform(put("/api/openinghours/rule")
            .param("name", "Test Name")
            .param("rule", "Test Rule")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Test Name"))
            .andExpect(jsonPath("$.rule").value("Test Rule"))
    }

    @Test
    fun `test delete opening hours`() {
        val id = UUID.randomUUID()

        Mockito.`when`(service.delete(id)).thenReturn(true)

        mockMvc.perform(delete("/api/openinghours/rule/$id"))
            .andExpect(status().isOk)
            .andExpect(content().string("true"))
    }

    @Test
    fun `test get all opening hours`() {
        val openingHoursList = listOf(
            Rule(UUID.randomUUID(), "Test Name 1", "Test Rule 1", "Header 1", "Text 1", false),
            Rule(UUID.randomUUID(), "Test Name 2", "Test Rule 2", "Header 2", "Text 2", false)
        )

        Mockito.`when`(service.getAll()).thenReturn(openingHoursList)

        mockMvc.perform(get("/api/openinghours/rule"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Test Name 1"))
            .andExpect(jsonPath("$[1].name").value("Test Name 2"))
    }

    @Test
    fun `test update opening hours`() {
        val id = UUID.randomUUID()
        val updatedOpeningHours = Rule(id, "Updated Name", "Updated Rule", "Header", "Text", false)

        Mockito.`when`(service.update(id, "Updated Name", "Updated Rule")).thenReturn(updatedOpeningHours)

        mockMvc.perform(patch("/api/openinghours/rule/$id")
            .param("name", "Updated Name")
            .param("rule", "Updated Rule")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.rule").value("Updated Rule"))
    }
}*/
