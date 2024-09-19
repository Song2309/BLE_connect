/***************************************************************************//**
 * @file
 * @brief Core application logic.
 *******************************************************************************
 * # License
 * <b>Copyright 2020 Silicon Laboratories Inc. www.silabs.com</b>
 *******************************************************************************
 *
 * SPDX-License-Identifier: Zlib
 *
 * The licensor of this software is Silicon Laboratories Inc.
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 *
 ******************************************************************************/
#include "em_common.h"
#include "app_assert.h"
#include "sl_bluetooth.h"
#include "gatt_db.h"
#include "app.h"
#include "sl_sensor_rht.h"
#include "em_emu.h"

// The advertising set handle allocated from Bluetooth stack.
static uint8_t advertising_set_handle = 0xff;
static uint32_t humidity = 0;
static uint32_t temperature = 0;
static uint8_t temp = 0;
static uint8_t hum = 0;
static uint8_t connection_handle = 0;

#define READING_INTERVAL_MSEC 5000
#define APP_TIMER_EXT_SIGNAL  0x01
static sl_sleeptimer_timer_handle_t app_timer;
static bool temperature_notify_enable = false;
static bool humidity_notify_enable = false;
uint16_t temperature_char_handle;
uint16_t humidity_char_handle;


static void temp_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data);
static void humd_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data);
static void app_timer_callback(sl_sleeptimer_timer_handle_t *timer,
                               void *data);
static void temp_notify_update();
static void hum_notify_update();
static void update();

/**************************************************************************//**
 * Application Init.
 *****************************************************************************/
SL_WEAK void app_init(void)
{
  /////////////////////////////////////////////////////////////////////////////
  // Put your additional application init code here!                         //
  sl_sensor_rht_init();
  // This is called once during start-up.                                    //
  /////////////////////////////////////////////////////////////////////////////
}

/**************************************************************************//**
 * Application Process Action.
 *****************************************************************************/
SL_WEAK void app_process_action(void)
{
  /////////////////////////////////////////////////////////////////////////////
  // Put your additional application code here!                              //
  // This is called infinitely.                                              //
  // Do not call blocking functions from here!                               //
  /////////////////////////////////////////////////////////////////////////////
}

/**************************************************************************//**
 * Bluetooth stack event handler.
 * This overrides the dummy weak implementation.
 *
 * @param[in] evt Event coming from the Bluetooth stack.
 *****************************************************************************/
void sl_bt_on_event(sl_bt_msg_t *evt)
{
  sl_status_t sc;

  switch (SL_BT_MSG_ID(evt->header)) {
    // -------------------------------
    // This event indicates the device has started and the radio is ready.
    // Do not call any stack command before receiving this boot event!
    case sl_bt_evt_system_boot_id:
      // Create an advertising set.
      sc = sl_bt_advertiser_create_set(&advertising_set_handle);
      app_assert_status(sc);

      // Generate data for advertising
      sc = sl_bt_legacy_advertiser_generate_data(advertising_set_handle,
                                                 sl_bt_advertiser_general_discoverable);
      app_assert_status(sc);

      // Set advertising interval to 100ms.
      sc = sl_bt_advertiser_set_timing(
          advertising_set_handle,
          160, // min. adv. interval (milliseconds * 1.6)
          160, // max. adv. interval (milliseconds * 1.6)
          0,   // adv. duration
          0);  // max. num. adv. events
      app_assert_status(sc);
      // Start advertising and enable connections.
      sc = sl_bt_legacy_advertiser_start(advertising_set_handle,
                                         sl_bt_legacy_advertiser_connectable);
      /*sl_bt_advertiser_set_channel_map(advertising_set_handle, 1);
      sl_bt_system_set_tx_power(0,0,0,0);*/
      //sl_bt_advertiser_set_configuration(advertising_set_handle, 0);
      app_assert_status(sc);
      break;

      // -------------------------------
      // This event indicates that a new connection was opened.
    case sl_bt_evt_connection_opened_id:
      temperature_notify_enable = false;
      humidity_notify_enable = false;
      connection_handle = evt->data.evt_connection_opened.connection;
      init_timer();
      break;

      // -------------------------------
      // This event indicates that a connection was closed.
    case sl_bt_evt_connection_closed_id:
      connection_handle = 0;
      // Generate data for advertising
      sc = sl_bt_legacy_advertiser_generate_data(advertising_set_handle,
                                                 sl_bt_advertiser_general_discoverable);
      app_assert_status(sc);

      // Restart advertising after client has disconnected.
      sc = sl_bt_legacy_advertiser_start(advertising_set_handle,
                                         sl_bt_legacy_advertiser_connectable);
      app_assert_status(sc);
      break;

      ///////////////////////////////////////////////////////////////////////////
      // Add additional event handlers here as your application requires!      //
      ///////////////////////////////////////////////////////////////////////////
    case sl_bt_evt_gatt_server_user_read_request_id:
      switch (evt->data.evt_gatt_server_user_read_request.characteristic) {
        case gattdb_temperature:
          temp_read_cb(&evt->data.evt_gatt_server_user_read_request);
          break;
        case gattdb_humidity:
          humd_read_cb(&evt->data.evt_gatt_server_user_read_request);
          break;
      }
        case sl_bt_evt_gatt_server_characteristic_status_id:
          if (evt->data.evt_gatt_server_characteristic_status.characteristic
              == gattdb_temperature) {
              temperature_char_handle = gattdb_temperature;
              // client characteristic configuration changed by remote GATT client
              if (evt->data.evt_gatt_server_characteristic_status.status_flags
                  == sl_bt_gatt_server_client_config) {
                  if (evt->data.evt_gatt_server_characteristic_status.
                      client_config_flags == sl_bt_gatt_notification) {
                      temperature_notify_enable = true;
                  } else {
                      temperature_notify_enable = false;
                      sl_sleeptimer_stop_timer(&app_timer);
                  }
              }
          } else if (evt->data.evt_gatt_server_characteristic_status.characteristic
              == gattdb_humidity) {
              humidity_char_handle = gattdb_humidity;
              if (evt->data.evt_gatt_server_characteristic_status.status_flags
                  == sl_bt_gatt_server_client_config) {
                  if (evt->data.evt_gatt_server_characteristic_status.
                      client_config_flags == sl_bt_gatt_notification) {
                      humidity_notify_enable = true;
                  } else {
                      humidity_notify_enable = false;
                      sl_sleeptimer_stop_timer(&app_timer);
                  }
              }
          }
          break;
        case sl_bt_evt_system_external_signal_id:
          update();
          break;
          // -------------------------------
          // Default event handler.
        default:
          break;
  }
}
void init_timer(void) {
  sl_status_t sc = sl_sleeptimer_start_periodic_timer_ms(&app_timer, READING_INTERVAL_MSEC, app_timer_callback, NULL, 0, 0);
  app_assert_status(sc);
}
static void app_timer_callback(sl_sleeptimer_timer_handle_t *timer, void *data) {
  (void)timer;
  (void)data;

  sl_bt_external_signal(APP_TIMER_EXT_SIGNAL);
}
static void update(void)
{
  if (temperature_notify_enable){
      temp_notify_update();
  }
  if (humidity_notify_enable) {
      hum_notify_update();
  }
}
static void temp_notify_update() {
  sl_status_t sc;
  sl_sensor_rht_get(0, &temperature);
  uint8_t value = temperature / 1000;
  if (value != temp) {
      temp = value;
      sc = sl_bt_gatt_server_send_notification(connection_handle, temperature_char_handle, 1, &value);
      app_assert_status(sc);
  }
}

static void hum_notify_update() {
  sl_status_t sc;
  sl_sensor_rht_get(&humidity, 0);
  uint8_t value = humidity / 1000;
  if (value != hum ) {
      hum = value;
      sc = sl_bt_gatt_server_send_notification(connection_handle, humidity_char_handle, 1, &value);
      app_assert_status(sc);
  }
}
static void temp_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data)
{
  sl_status_t sc;
  sl_sensor_rht_get(0, &temperature);
  uint8_t value = temperature/1000;

  sc = sl_bt_gatt_server_send_user_read_response(
      data->connection,
      data->characteristic,
      0,
      1,
      &value,
      NULL);
  app_assert_status(sc);
}
static void humd_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data)
{
  sl_status_t sc;
  sl_sensor_rht_get(&humidity, 0);
  uint8_t value1 = humidity/1000;
  sc = sl_bt_gatt_server_send_user_read_response(
      data->connection,
      data->characteristic,
      0,
      1,
      &value1,
      NULL);
  app_assert_status(sc);
}
