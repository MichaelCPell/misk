package misk.moshi.wire

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import org.assertj.core.api.isEqualToAsJson
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
internal class WireMessageAdapterTest {
    @MiskTestModule
    val module = Modules.combine(MoshiModule())

    @Inject
    lateinit var moshi: Moshi

    @Test
    fun simpleTypes() {
        val warehouseAdapter = moshi.adapter(Warehouse::class.java)

        val warehouse = Warehouse.Builder()
                .warehouse_id(1014L)
                .warehouse_token("AAAA")
                .build()

        val json = warehouseAdapter.indent(" ").toJson(warehouse)
        assertThat(json).isEqualToAsJson("""
{
 "warehouse_id": 1014,
 "warehouse_token": "AAAA"
}""")

        assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
    }

    @Test
    fun nestedTypes() {
        val shipmentAdapter = moshi.adapter(Shipment::class.java)

        val shipment = Shipment.Builder()
                .shipment_id(100075)
                .shipment_token("P_AAAAA")
                .status(Shipment.State.DELIVERING)
                .source(Warehouse.Builder()
                        .warehouse_id(9999L)
                        .warehouse_token("C_RANDY")
                        .build())
                .destination(Warehouse.Builder()
                        .warehouse_id(7777L)
                        .warehouse_token("C_CATHY")
                        .build())
                .deleted(true)
                .load_ratio(0.75)
                .notes(listOf("Note A", "Note B", "Note C"))
                .build()

        val json = shipmentAdapter.indent(" ").toJson(shipment)
        assertThat(json).isEqualToAsJson("""
{
 "shipment_id": 100075,
 "shipment_token": "P_AAAAA",
 "source": {
  "warehouse_id": 9999,
  "warehouse_token": "C_RANDY"
 },
 "destination": {
  "warehouse_id": 7777,
  "warehouse_token": "C_CATHY"
 },
 "status": "DELIVERING",
 "load_ratio": 0.75,
 "deleted": true,
 "notes": [
  "Note A",
  "Note B",
  "Note C"
 ]
}""")
        assertThat(shipmentAdapter.fromJson(json)).isEqualTo(shipment)
    }

    @Test
    fun missingListFieldsMapToEmptyLists() {
        val shipmentAdapter = moshi.adapter(Shipment::class.java)
        val parsed = shipmentAdapter.fromJson("""
{
 "shipment_id": 100075,
 "shipment_token": "P_AAAAA",
 "load_ratio": 0.75,
 "deleted": true
}""") ?: throw IllegalArgumentException("could not parse")

        val expected = Shipment.Builder()
                .shipment_id(100075)
                .shipment_token("P_AAAAA")
                .deleted(true)
                .load_ratio(0.75)
                .build()

        assertThat(parsed).isEqualTo(expected)
        assertThat(parsed.notes).isNotNull
        assertThat(parsed.nested_shipments).isNotNull
    }

    @Test
    fun maps() {
        val warehouseAdapter = moshi.adapter(Warehouse::class.java)
        val warehouse = Warehouse.Builder()
                .warehouse_id(1976)
                .warehouse_token("W_ACDFD")
                .dropoff_points(mapOf(
                        "station-1" to "left of north door A",
                        "station-2" to "right of office",
                        "station-3" to "left of center"
                ))
                .build()
        val json = warehouseAdapter.indent(" ").toJson(warehouse)
        assertThat(json).isEqualToAsJson("""
{
 "warehouse_id": 1976,
 "warehouse_token": "W_ACDFD",
 "dropoff_points": {
  "station-1": "left of north door A",
  "station-2": "right of office",
  "station-3": "left of center"
 }
}""")
        assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
    }

    @Test
    fun detectsAndFailsOnMultipleOneOfs() {
        val shipmentAdapter = moshi.adapter(Shipment::class.java)
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            shipmentAdapter.fromJson("""
{
 "shipment_id": 100075,
 "shipment_token": "P_AAAAA",
 "load_ratio": 0.75,
 "deleted": true,
 "account_token": "AC_5765",
 "card_token": "CC_34531"
}""")
        }).hasMessage("at most one of account_token, card_token, transfer_id may be non-null")
    }

    @Test
    fun cyclicalTypes() {
        val warehouseAdapter = moshi.adapter(Warehouse::class.java)
        val warehouse = Warehouse.Builder()
                .warehouse_token("CDCDC")
                .warehouse_id(755L)
                .central_repo(Warehouse.Builder()
                        .warehouse_id(1L)
                        .warehouse_token("AAAAA")
                        .build())
                .alternates(listOf(
                        Warehouse.Builder()
                                .warehouse_id(756)
                                .warehouse_token("CDCDB")
                                .build(),
                        Warehouse.Builder()
                                .warehouse_id(757)
                                .warehouse_token("CDCDA")
                                .build()))
                .build()

        val json = warehouseAdapter.indent(" ").toJson(warehouse)
        assertThat(json).isEqualToAsJson("""
{
 "warehouse_id": 755,
 "warehouse_token": "CDCDC",
 "central_repo": {
  "warehouse_id": 1,
  "warehouse_token": "AAAAA"
 },
 "alternates": [
  {
   "warehouse_id": 756,
   "warehouse_token": "CDCDB"
  },
  {
   "warehouse_id": 757,
   "warehouse_token": "CDCDA"
  }
 ]
}""")
    }

    @Test
    fun byteStringsAreBase64() {
        val shipmentAdapter = moshi.adapter(Shipment::class.java)

        val shipment = Shipment.Builder()
                .shipment_id(100075)
                .shipment_token("P_AAAAA")
                .source_signature(ByteString.encodeUtf8("98 34v59823wh;tiejs"))
                .build()
        val jsonText = shipmentAdapter.indent(" ").toJson(shipment)
        assertThat(jsonText).isEqualToAsJson("""
{
 "shipment_id": 100075,
 "shipment_token": "P_AAAAA",
 "source_signature": "OTggMzR2NTk4MjN3aDt0aWVqcw=="
}
            """)

    }
}