
package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    private Room room;

    @BeforeEach
    void setUp() {
        // ARRANGE: Chuẩn bị phòng mặc định còn trống
        room = new Room("R101", 500000, true);
    }

    @Test
    @DisplayName("Đặt phòng thành công")
    void testBookRoom_Success() {
        // ARRANGE
        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));
        when(paymentGateway.charge("123456", 1000000))
                .thenReturn(true);

        // ACT: Đặt phòng 2 đêm
        BookingReceipt receipt =
                bookingService.bookRoom("R101", 2, "123456");

        // ASSERT
        assertNotNull(receipt);
        assertEquals("R101", receipt.getRoomId());
        assertEquals(1000000, receipt.getTotalAmount(), 0.001);
        assertNotNull(receipt.getBookingId());
        assertFalse(room.isAvailable());

        verify(roomRepository, times(1)).findById("R101");
        verify(paymentGateway, times(1))
                .charge("123456", 1000000);
        verify(roomRepository, times(1)).save(room);
    }

    @Test
    @DisplayName("Không tìm thấy phòng")
    void testBookRoom_RoomNotFound_ThrowsException() {
        // ARRANGE
        when(roomRepository.findById("R999"))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom("R999", 2, "123456")
        );

        assertEquals("Khong tim thay phong: R999",
                exception.getMessage());

        // Không được thanh toán hoặc lưu phòng
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Phòng đã được đặt trước")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        // ARRANGE
        room.setAvailable(false);
        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));

        // ACT + ASSERT
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom("R101", 2, "123456")
        );

        assertEquals("Phong da duoc dat truoc do: R101",
                exception.getMessage());

        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Thanh toán thất bại")
    void testBookRoom_PaymentFailed_ThrowsException() {
        // ARRANGE
        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));
        when(paymentGateway.charge("123456", 1000000))
                .thenReturn(false);

        // ACT + ASSERT
        PaymentFailedException exception = assertThrows(
                PaymentFailedException.class,
                () -> bookingService.bookRoom("R101", 2, "123456")
        );

        assertEquals("Thanh toan qua the credit card that bai!",
                exception.getMessage());

        // Thanh toán lỗi thì phòng vẫn còn trống
        assertTrue(room.isAvailable());
        verify(paymentGateway, times(1))
                .charge("123456", 1000000);
        verify(roomRepository, never()).save(any(Room.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Số đêm không hợp lệ")
    void testBookRoom_InvalidNights_ThrowsException(int nights) {
        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", nights, "123456")
        );

        // Dữ liệu sai phải bị từ chối trước khi gọi phụ thuộc
        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Thẻ thanh toán null hoặc rỗng")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(
            String creditCard) {

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", 2, creditCard)
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Mã phòng null")
    void testBookRoom_NullRoomId_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom(null, 2, "123456")
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }
}
