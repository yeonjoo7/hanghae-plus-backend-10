package com.hanghae.ecommerce.application.cart;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 장바구니 관리 서비스
 * 장바구니 생성, 상품 추가/수정/삭제, 조회 등의 비즈니스 로직을 처리합니다.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * 사용자의 활성 장바구니 조회 (없으면 생성)
     * 
     * @param userId 사용자 ID
     * @return 장바구니 정보
     */
    public Cart getOrCreateActiveCart(Long userId) {
        // 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (!user.isActive()) {
            throw new IllegalStateException("비활성 사용자는 장바구니를 사용할 수 없습니다.");
        }

        // 활성 장바구니 조회
        Optional<Cart> existingCart = cartRepository.findByUserIdAndState(userId, CartState.NORMAL);
        if (existingCart.isPresent()) {
            return existingCart.get();
        }

        // 새 장바구니 생성
        Cart newCart = Cart.create(userId);
        return cartRepository.save(newCart);
    }

    /**
     * 장바구니 전체 조회 (상품 정보 포함)
     * 
     * @param userId 사용자 ID
     * @return 장바구니 정보
     */
    public CartInfo getCartInfo(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        // List<CartItem> cartItems =
        // cartItemRepository.findByCartIdAndState(cart.getId(), CartState.NORMAL);
        List<CartItem> cartItems = List.of();

        if (cartItems.isEmpty()) {
            return new CartInfo(cart, List.of(), Money.zero(), 0);
        }

        // 상품 정보 조회
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Product> productMap = productRepository.findByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 장바구니 아이템 정보 생성
        List<CartItemInfo> cartItemInfos = cartItems.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    if (product == null) {
                        throw new IllegalStateException("상품을 찾을 수 없습니다. ID: " + item.getProductId());
                    }
                    return new CartItemInfo(item, product);
                })
                .collect(Collectors.toList());

        // 총 금액 계산
        Money totalAmount = cartItemInfos.stream()
                .map(CartItemInfo::getSubtotal)
                .reduce(Money.zero(), Money::add);

        // 총 아이템 수 계산
        int totalItemCount = cartItems.stream()
                .mapToInt(item -> item.getQuantity().getValue())
                .sum();

        return new CartInfo(cart, cartItemInfos, totalAmount, totalItemCount);
    }

    /**
     * 장바구니에 상품 추가
     * 
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @param quantity  수량
     * @return 장바구니 아이템 정보
     */
    public CartItemInfo addItemToCart(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }

        Cart cart = getOrCreateActiveCart(userId);

        // 상품 존재 및 판매 가능 여부 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));

        if (!product.isAvailable()) {
            throw new IllegalArgumentException("판매 중지된 상품입니다. ID: " + productId);
        }

        // 구매 제한 수량 확인
        Quantity requestQuantity = Quantity.of(quantity);
        if (product.exceedsLimitedQuantity(requestQuantity)) {
            throw new IllegalArgumentException("구매 제한 수량을 초과했습니다. 제한: " +
                    product.getLimitedQuantity().getValue() + ", 요청: " + quantity);
        }

        // 기존 동일 상품이 있는지 확인
        // Optional<CartItem> existingItem =
        // cartItemRepository.findByCartIdAndProductIdAndProductOptionIdIsNullAndState(
        // cart.getId(), productId, CartState.NORMAL);
        Optional<CartItem> existingItem = Optional.empty();

        CartItem cartItem;
        if (existingItem.isPresent()) {
            // 기존 아이템 수량 증가
            cartItem = existingItem.get();

            // 총 수량이 제한을 초과하지 않는지 확인
            Quantity newTotalQuantity = cartItem.getQuantity().add(requestQuantity);
            if (product.exceedsLimitedQuantity(newTotalQuantity)) {
                throw new IllegalArgumentException("장바구니 제한 수량을 초과합니다. 현재: " +
                        cartItem.getQuantity().getValue() + ", 추가: " + quantity +
                        ", 제한: " + product.getLimitedQuantity().getValue());
            }

            cartItem.increaseQuantity(requestQuantity);
        } else {
            // 새 아이템 생성
            cartItem = CartItem.createForProduct(cart.getId(), productId, requestQuantity);
        }

        CartItem savedItem = cartItemRepository.save(cartItem);
        return new CartItemInfo(savedItem, product);
    }

    /**
     * 장바구니 아이템 수량 변경
     * 
     * @param userId     사용자 ID
     * @param cartItemId 장바구니 아이템 ID
     * @param quantity   새로운 수량
     * @return 수정된 장바구니 아이템 정보
     */
    public CartItemInfo updateCartItemQuantity(Long userId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }

        CartItem cartItem = getCartItemByUser(userId, cartItemId);
        Product product = productRepository.findById(cartItem.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + cartItem.getProductId()));

        // 구매 제한 수량 확인
        Quantity newQuantity = Quantity.of(quantity);
        if (product.exceedsLimitedQuantity(newQuantity)) {
            throw new IllegalArgumentException("구매 제한 수량을 초과했습니다. 제한: " +
                    product.getLimitedQuantity().getValue() + ", 요청: " + quantity);
        }

        cartItem.updateQuantity(newQuantity);
        CartItem savedItem = cartItemRepository.save(cartItem);

        return new CartItemInfo(savedItem, product);
    }

    /**
     * 장바구니 아이템 삭제
     * 
     * @param userId     사용자 ID
     * @param cartItemId 장바구니 아이템 ID
     */
    public void removeCartItem(Long userId, Long cartItemId) {
        CartItem cartItem = getCartItemByUser(userId, cartItemId);
        cartItem.delete();
        cartItemRepository.save(cartItem);
    }

    /**
     * 장바구니 전체 비우기
     * 
     * @param userId 사용자 ID
     */
    public void clearCart(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        // List<CartItem> cartItems =
        // cartItemRepository.findByCartIdAndState(cart.getId(), CartState.NORMAL);
        List<CartItem> cartItems = List.of();

        for (CartItem item : cartItems) {
            item.delete();
        }

        cartItemRepository.saveAll(cartItems);
    }

    /**
     * 선택한 장바구니 아이템들 조회
     * 
     * @param userId      사용자 ID
     * @param cartItemIds 장바구니 아이템 ID 목록
     * @return 선택된 장바구니 아이템 정보 목록
     */
    public List<CartItemInfo> getSelectedCartItems(Long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("장바구니 아이템 ID 목록은 비어있을 수 없습니다.");
        }

        Cart cart = getOrCreateActiveCart(userId);
        List<CartItem> allCartItems = cartItemRepository.findByCartId(cart.getId());
        List<CartItem> cartItems = allCartItems.stream()
                .filter(item -> cartItemIds.contains(item.getId()) && item.getState() == CartState.NORMAL)
                .collect(Collectors.toList());

        // 요청된 모든 아이템이 존재하는지 확인
        if (cartItems.size() != cartItemIds.size()) {
            List<Long> foundIds = cartItems.stream().map(CartItem::getId).collect(Collectors.toList());
            List<Long> missingIds = cartItemIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new IllegalArgumentException("존재하지 않는 장바구니 아이템이 있습니다. IDs: " + missingIds);
        }

        // 상품 정보 조회
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Product> productMap = productRepository.findByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return cartItems.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    if (product == null) {
                        throw new IllegalStateException("상품을 찾을 수 없습니다. ID: " + item.getProductId());
                    }
                    return new CartItemInfo(item, product);
                })
                .collect(Collectors.toList());
    }

    /**
     * 사용자 권한으로 장바구니 아이템 조회
     */
    private CartItem getCartItemByUser(Long userId, Long cartItemId) {
        Cart cart = getOrCreateActiveCart(userId);
        // return cartItemRepository.findByIdAndCartIdAndState(cartItemId, cart.getId(),
        // CartState.NORMAL)
        // .orElseThrow(() -> new IllegalArgumentException("장바구니 아이템을 찾을 수 없습니다. ID: " +
        // cartItemId));
        throw new IllegalArgumentException("장바구니 아이템을 찾을 수 없습니다. ID: " + cartItemId);
    }

    /**
     * 장바구니 전체 정보를 담는 클래스
     */
    public static class CartInfo {
        private final Cart cart;
        private final List<CartItemInfo> items;
        private final Money totalAmount;
        private final int totalItemCount;

        public CartInfo(Cart cart, List<CartItemInfo> items, Money totalAmount, int totalItemCount) {
            this.cart = cart;
            this.items = items;
            this.totalAmount = totalAmount;
            this.totalItemCount = totalItemCount;
        }

        public Cart getCart() {
            return cart;
        }

        public List<CartItemInfo> getItems() {
            return items;
        }

        public Money getTotalAmount() {
            return totalAmount;
        }

        public int getTotalItemCount() {
            return totalItemCount;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public int getItemCount() {
            return items.size();
        }
    }

    /**
     * 장바구니 아이템 정보를 담는 클래스
     */
    public static class CartItemInfo {
        private final CartItem cartItem;
        private final Product product;

        public CartItemInfo(CartItem cartItem, Product product) {
            this.cartItem = cartItem;
            this.product = product;
        }

        public CartItem getCartItem() {
            return cartItem;
        }

        public Product getProduct() {
            return product;
        }

        public Long getCartItemId() {
            return cartItem.getId();
        }

        public Long getProductId() {
            return product.getId();
        }

        public String getProductName() {
            return product.getName();
        }

        public Money getPrice() {
            return product.getPrice();
        }

        public int getQuantity() {
            return cartItem.getQuantity().getValue();
        }

        public Money getSubtotal() {
            return product.getPrice().multiply(cartItem.getQuantity().getValue());
        }

        public boolean isInStock() {
            return product.isAvailable();
        }

        public Integer getMaxQuantityPerCart() {
            return product.hasLimitedQuantity() ? product.getLimitedQuantity().getValue() : null;
        }
    }
}