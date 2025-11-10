# 이커머스 시스템 설계 문서

## 목차
1. [개요](#개요)
2. [시퀀스 다이어그램](#시퀀스-다이어그램)
3. [ERD (Entity Relationship Diagram)](#erd-entity-relationship-diagram)
4. [관련 문서](#관련-문서)

---

## 개요

본 문서는 이커머스 시스템의 주요 프로세스를 시각화하고, 데이터 모델을 설명합니다.

### 주요 기능
- 상품 조회 및 장바구니 담기
- 재고 확인 및 차감
- 주문 생성 및 쿠폰 적용
- 결제 처리 (포인트 기반)
- 에러 상황 처리 및 보상 트랜잭션

### 기술 스택
- **DBMS**: MySQL 8.0+
- **인증**: JWT Bearer Token
- **동시성 제어**: 비관적 락 (재고), 분산 락 (쿠폰)

---

## 시퀀스 다이어그램

### 주문 및 결제 프로세스

다음은 장바구니 담기부터 결제 완료까지의 전체 프로세스를 나타낸 시퀀스 다이어그램입니다.

#### 상세 시퀀스 다이어그램

다음 이미지는 에러 처리 및 보상 트랜잭션을 포함한 전체 프로세스를 보여줍니다.

##### 1. 장바구니 담기 및 주문 생성 프로세스

![장바구니 담기 및 주문 생성](https://www.plantuml.com/plantuml/png/nLTVRzDM57_tfpZg2tHh-JFnL2c8D7R1exMpn1dRL2cOs1hMKhl43XERa58QK5UAG5l3IfMmLFoXJ9raQ62TnBJlOrz_Xrrxrybrk9cXUUX3rMldd7FF_PtVEVVdSxQZrfpwKfLp3AUg0thopjyx0D-z3zvh5tNR46ms_PqsQXwWzQU0xhQ3HYDS-NUVeauFFUnvVnxuZtSvJgquLWsy_QxdTa2r8LUt7Mj9ht7BU0EZOYohfWCJsRm8vrL7_rwzCO6judNIY2I1cct_hoRUBBAKQvfUA-grwqP57p9_l8Ut7J8lEbRbkn7cptntqtkpvzzT6tBAOJkc3y4YQPrNRopf9jiXkB-7EWV1-i6mZsKCF3JLKQ-ejWuJF5b5DlminsKoGEU9Vdq0Q7Kb-7aD0z_p3bp8P3WkXXYcpyP0pa1UBYfmieAJiKyQZhvaBve_BjSihLvnH6qAhjLnVePpusPOCECwzkhNW2EBHBZFBoaGtEa6AoucH0Czxt1grKc-mQnu6cpiSW00_H0uHfpU39mvTHfAfbfthberumTTmobLB4sVWOcIb2qf5-I2-8t0JzoCGcZwUvdffaPdHzTf1fBrVBlfkGr0svlezH-38zA5vQW7Hh3RVlSP-FTSpEwW6HznqJpEm13Emgd928_tt52x6spl1gqjz09dqrf7dOteS8czAPmnHATWLjMWe5-hwxOpG4YKlipEYtnPbF8bXK34H7au106JYS5ed8VXIJJI391U2nwrCBq9p0De9lk89_kFwp-HeFpi31I5UI6dm2SmLv0NWCHOD2zT40e261fae1ztqSnAFDXE63W3noIvi92TFpRO37SCFmlJmzd6BOEwBN-_1xwx5JpQuZITzWlBH-nmwG3jzF0HtwzRyW13hC78dm79Sc3EgfjQIfrfqq6LymMPB-MKiYGhvJcv9F4fbHxUJzEPo1-M0r6CD1AWjIt_jvy85t27sA5HUT0S6OZNSPD4RlxR1jfvbOugm_umtLCipSs9EL6Gb79HaNCNnscXyQ4bVEksWrOx1X3rrhpzTt2yQYmPZgwL-wYbOyICT4YeXAzoWi0BV7bUN10L_F-BKbPIHENheqOi1O7aH6g-ylRVSEmCXcO_cKEO8a91mVyK6S9hinpUcfZETLklbGrjAXv3qPfYT6Bui2hJm3SJe4TD_yd6yD0PMPtFC9ponR0es4B4YP3XTPDZma_ikH6eCX2Db0NHLwiT35UQah12tHMA2FkgiRnicDzcDQscs_OKLApwicMAcdsaMk93DGEj7sUI40pBXQJMvEWdAbAgOl1T5ztPmHS9gU4Ke7-wmNrtaliupK3sEnBHGFPERr9YcEYiMU6yA85Io4h5R4uHPObkcgHBYghRSOFjZVzLTTIQAJ2erYOIW_GtPABLI951zpOxkLOpBE3jDlY_l_HtEibK67nX-JB42HM648Ro34Lk-5dn4CkLDIPH20W5UNv-DfkxU5WcvR85fImivCUx1lZPyOcLhaUZprRaxdhk0y8Lt8KO9Q_NGDqLy3j_Zo5G8tL3k4DT2O0PFX30zkIYEISNe9Jdiuhm8QBqECuAWkrx_YyTF1UgbhKCdhj2ZFpzPlA434Km75wzhXfLzKfLPognUF16WTYYZDw2pII64OFexCYqpRWQxkFeoAPacbv9eosRkj5F8Ntn7vDSX24ngug2mYfSXlLokb_CgKKptCUsgXhjatp-j6_E82_rZSKcAoaYQIeYPZXsvTbEAP8ivNebLoQv6t6e_lKwtV_fpWPgnmqIlR-rXxPV0XwH_lgpg9DMszJF_eiDV0Zm3bpFRLBlWzL6U2NlDmS1sbXN4uaTZM2o9KEhf8pUiIxnd5pAoz9HZrzsTNeT8gKmdckejGFUVXFTSZ6UP8J5xuFMI_mJLRQWrOq4UtS5OqtlmrRcboCTUfdMvOxbgDMokcJLJMSAJfmuCHbnE4Nh4GwdRXZj5tyBa8SH7mFfcrCNVMeQP3OGWVAVo8ucn42Rb8e2VonkPt87zTKuEn42Uynjz7hBVxx7o295YaMYOzl5pPEknbY7ecHOyaWylcmLSYgMEcFgC7Pcv5GfEemOiGKNkouvUM51LB0BMyOoFkPEGwwcuqSDTtN4ePJFESg36zMjZ4vDPdGEDsvzgVel)

##### 2. 결제 처리 및 보상 트랜잭션

![결제 처리 및 보상 트랜잭션](https://www.plantuml.com/plantuml/png/pLVXRzDM4F_kl-BKBxIZxT34lY05uIGEU2HsbBYWIPM2YIqjMce7n-bMJKXXXAbgGMnQknMKLAaeh1-A56ZPYiIq_oT-_X_sNd9kybmxXHMaVOhVozsxkz_zxjwzAtLNTzp6KbLmAsxL1FBidRT_15xlCVHVzqYt3Vv6ozjfazOHMTi5ijxscqtsvQtlaiuX-Usm_zUHjxqd27hPjHte7tJxlGxeTKWrwgwzP3f2ZHgeb2irtN9XIipBS5LtpU_qbIacXT-y43f1MctlROiQ6qYgZc4wHTDPhfJDaFZsFZKR4Y-wTldR6F7dNc-Z_sRVMryDAQMeNAGEOi5Bv_ML9TEALl0VxvFEaRzs5DQnAoCDGtVrsthTXAaqVWraqaaXaO30vEijxyK-91A2420ASvS3t2v1NYrgy7bjw4fzqVh1PZZ9nYpWNivqlx7fKb-o6vPxb-MfiaorZw4Ny8CxzgkR6lW_TVrxFUf8axpeS69yv9UEVNswGLw_75a80IJmwwY3oFO3h_iSl4SzIYw_5HXVjAQ3i30Ek0mNPYZfgkv97TBk-a_t_CqjyaSJoEOQwJmK0032viVZlNZX0YHr0mhcdOPPTocEPTkW3at9oWqnAwTBif9VqARk3Wv2hS49GL9E736GDCmuCF3VS39CouZ2APsy14dfggo0LX2LefZIP5KP0PrEnYaLfQoKqk0po1JK7DmQe7XhqRfvJIf8K34W0GZieYKgQMZKJQV4jkdNS3UZ5c0XdnOrIKWdOIugPrYjtKtlu12clSxVu3zzvFtQ6UO7_wUv0R9pI6lrrCIWwmKrcqsAgUllaRkBeDWkP2WnZ57cr49QAfGKLIjbr0KbFIbt_17XtAIJPyZb204Wz-_vzzlzNXCKjP0JimD8DblU-jR7GU4K1W_nA6eqbMVWyTdGu4Kdj9B-qIhPt1cLFrPz0lawxzgkNYqDTv5dlARtvohfl2DlFossOevIIolbv69Er5BN_a-usfKnLCUke-qD1e__P9DkZ3e8Toy9k8hg9FgoNgdgjwjcgSQ45gr1Os7TWkigyUrZicF3L8PtQJQRPESLWu3JZ-f7lCedhgpYGYOZfsI9SY0fPaKbzN5BYmls3FI8o2Tc1JDNb4va5X8dTkRmyfrTj020gHg1xFZYF8f54E9K4lJs-hsVWJnfUSyUHljDGPSA6ltHLBYjLtMhR9PSHxVgB7JRehq2Bx5PS5TgPk9SNlmwHnDqRZRm7kRdvsTYF5cZmsoRRBya3xPe9-bvEntUcLZYSM4SZukl-WTlXBXyOjLE2X1dE9n_M5IZA9PmiAkxkal3JAcvV5RIfFGvZ2ssj-9DJRfDEWgFmaFXI9RWu30Woj0W982QL39o8KTDiccpOfHqnWH5lJaz4pLYnBZ5Nuz0Vd-0enm5Sh9pDEISh0by1GcJIlaBMe3gTLh122nYSLU8hjT0dCzdV0r6rD_uAB--wp_SOsyee3Pfk3pTs8XlsLJLNZQTqCcpBDwJvtW_iiS1sThbHq7MGldd0zdz1SZMAdd2ddPjShyJqnV3gP6-BvirLbQ1sdlqKvJvS3fXePOoefobd8ga43lwWsaKvn6-4L4Vdv7UmHlwDiItp40nm9QJP__WHcI8TVPNkB7UQUYMMt5N816rUHw2JsomTRjgZElmwxbZyG2fccCRZR8x7E1nmMCsAMAijN1SqLYFP0qp6f_JMJ3eqsMxKREj41F46vG2OZ8hZPD0Lc1wA2qRzPc9JFYdwp_kyN6XF_-fusA7HLxY_H5l7miKxm_iiyAfOyQNjDd8bciwbbu59c4w83cExOm63hv29euQ4qxZsiXWGE1Rw8Srk2lqWCPIzLy0)

---

**다이어그램 편집**:
- [다이어그램 1 편집](https://www.plantuml.com/plantuml/uml/nLTVRzDM57_tfpZg2tHh-JFnL2c8D7R1exMpn1dRL2cOs1hMKhl43XERa58QK5UAG5l3IfMmLFoXJ9raQ62TnBJlOrz_Xrrxrybrk9cXUUX3rMldd7FF_PtVEVVdSxQZrfpwKfLp3AUg0thopjyx0D-z3zvh5tNR46ms_PqsQXwWzQU0xhQ3HYDS-NUVeauFFUnvVnxuZtSvJgquLWsy_QxdTa2r8LUt7Mj9ht7BU0EZOYohfWCJsRm8vrL7_rwzCO6judNIY2I1cct_hoRUBBAKQvfUA-grwqP57p9_l8Ut7J8lEbRbkn7cptntqtkpvzzT6tBAOJkc3y4YQPrNRopf9jiXkB-7EWV1-i6mZsKCF3JLKQ-ejWuJF5b5DlminsKoGEU9Vdq0Q7Kb-7aD0z_p3bp8P3WkXXYcpyP0pa1UBYfmieAJiKyQZhvaBve_BjSihLvnH6qAhjLnVePpusPOCECwzkhNW2EBHBZFBoaGtEa6AoucH0Czxt1grKc-mQnu6cpiSW00_H0uHfpU39mvTHfAfbfthberumTTmobLB4sVWOcIb2qf5-I2-8t0JzoCGcZwUvdffaPdHzTf1fBrVBlfkGr0svlezH-38zA5vQW7Hh3RVlSP-FTSpEwW6HznqJpEm13Emgd928_tt52x6spl1gqjz09dqrf7dOteS8czAPmnHATWLjMWe5-hwxOpG4YKlipEYtnPbF8bXK34H7au106JYS5ed8VXIJJI391U2nwrCBq9p0De9lk89_kFwp-HeFpi31I5UI6dm2SmLv0NWCHOD2zT40e261fae1ztqSnAFDXE63W3noIvi92TFpRO37SCFmlJmzd6BOEwBN-_1xwx5JpQuZITzWlBH-nmwG3jzF0HtwzRyW13hC78dm79Sc3EgfjQIfrfqq6LymMPB-MKiYGhvJcv9F4fbHxUJzEPo1-M0r6CD1AWjIt_jvy85t27sA5HUT0S6OZNSPD4RlxR1jfvbOugm_umtLCipSs9EL6Gb79HaNCNnscXyQ4bVEksWrOx1X3rrhpzTt2yQYmPZgwL-wYbOyICT4YeXAzoWi0BV7bUN10L_F-BKbPIHENheqOi1O7aH6g-ylRVSEmCXcO_cKEO8a91mVyK6S9hinpUcfZETLklbGrjAXv3qPfYT6Bui2hJm3SJe4TD_yd6yD0PMPtFC9ponR0es4B4YP3XTPDZma_ikH6eCX2Db0NHLwiT35UQah12tHMA2FkgiRnicDzcDQscs_OKLApwicMAcdsaMk93DGEj7sUI40pBXQJMvEWdAbAgOl1T5ztPmHS9gU4Ke7-wmNrtaliupK3sEnBHGFPERr9YcEYiMU6yA85Io4h5R4uHPObkcgHBYghRSOFjZVzLTTIQAJ2erYOIW_GtPABLI951zpOxkLOpBE3jDlY_l_HtEibK67nX-JB42HM648Ro34Lk-5dn4CkLDIPH20W5UNv-DfkxU5WcvR85fImivCUx1lZPyOcLhaUZprRaxdhk0y8Lt8KO9Q_NGDqLy3j_Zo5G8tL3k4DT2O0PFX30zkIYEISNe9Jdiuhm8QBqECuAWkrx_YyTF1UgbhKCdhj2ZFpzPlA434Km75wzhXfLzKfLPognUF16WTYYZDw2pII64OFexCYqpRWQxkFeoAPacbv9eosRkj5F8Ntn7vDSX24ngug2mYfSXlLokb_CgKKptCUsgXhjatp-j6_E82_rZSKcAoaYQIeYPZXsvTbEAP8ivNebLoQv6t6e_lKwtV_fpWPgnmqIlR-rXxPV0XwH_lgpg9DMszJF_eiDV0Zm3bpFRLBlWzL6U2NlDmS1sbXN4uaTZM2o9KEhf8pUiIxnd5pAoz9HZrzsTNeT8gKmdckejGFUVXFTSZ6UP8J5xuFMI_mJLRQWrOq4UtS5OqtlmrRcboCTUfdMvOxbgDMokcJLJMSAJfmuCHbnE4Nh4GwdRXZj5tyBa8SH7mFfcrCNVMeQP3OGWVAVo8ucn42Rb8e2VonkPt87zTKuEn42Uynjz7hBVxx7o295YaMYOzl5pPEknbY7ecHOyaWylcmLSYgMEcFgC7Pcv5GfEemOiGKNkouvUM51LB0BMyOoFkPEGwwcuqSDTtN4ePJFESg36zMjZ4vDPdGEDsvzgVel)
- [다이어그램 2 편집](https://www.plantuml.com/plantuml/uml/pLVXRzDM4F_kl-BKBxIZxT34lY05uIGEU2HsbBYWIPM2YIqjMce7n-bMJKXXXAbgGMnQknMKLAaeh1-A56ZPYiIq_oT-_X_sNd9kybmxXHMaVOhVozsxkz_zxjwzAtLNTzp6KbLmAsxL1FBidRT_15xlCVHVzqYt3Vv6ozjfazOHMTi5ijxscqtsvQtlaiuX-Usm_zUHjxqd27hPjHte7tJxlGxeTKWrwgwzP3f2ZHgeb2irtN9XIipBS5LtpU_qbIacXT-y43f1MctlROiQ6qYgZc4wHTDPhfJDaFZsFZKR4Y-wTldR6F7dNc-Z_sRVMryDAQMeNAGEOi5Bv_ML9TEALl0VxvFEaRzs5DQnAoCDGtVrsthTXAaqVWraqaaXaO30vEijxyK-91A2420ASvS3t2v1NYrgy7bjw4fzqVh1PZZ9nYpWNivqlx7fKb-o6vPxb-MfiaorZw4Ny8CxzgkR6lW_TVrxFUf8axpeS69yv9UEVNswGLw_75a80IJmwwY3oFO3h_iSl4SzIYw_5HXVjAQ3i30Ek0mNPYZfgkv97TBk-a_t_CqjyaSJoEOQwJmK0032viVZlNZX0YHr0mhcdOPPTocEPTkW3at9oWqnAwTBif9VqARk3Wv2hS49GL9E736GDCmuCF3VS39CouZ2APsy14dfggo0LX2LefZIP5KP0PrEnYaLfQoKqk0po1JK7DmQe7XhqRfvJIf8K34W0GZieYKgQMZKJQV4jkdNS3UZ5c0XdnOrIKWdOIugPrYjtKtlu12clSxVu3zzvFtQ6UO7_wUv0R9pI6lrrCIWwmKrcqsAgUllaRkBeDWkP2WnZ57cr49QAfGKLIjbr0KbFIbt_17XtAIJPyZb204Wz-_vzzlzNXCKjP0JimD8DblU-jR7GU4K1W_nA6eqbMVWyTdGu4Kdj9B-qIhPt1cLFrPz0lawxzgkNYqDTv5dlARtvohfl2DlFossOevIIolbv69Er5BN_a-usfKnLCUke-qD1e__P9DkZ3e8Toy9k8hg9FgoNgdgjwjcgSQ45gr1Os7TWkigyUrZicF3L8PtQJQRPESLWu3JZ-f7lCedhgpYGYOZfsI9SY0fPaKbzN5BYmls3FI8o2Tc1JDNb4va5X8dTkRmyfrTj020gHg1xFZYF8f54E9K4lJs-hsVWJnfUSyUHljDGPSA6ltHLBYjLtMhR9PSHxVgB7JRehq2Bx5PS5TgPk9SNlmwHnDqRZRm7kRdvsTYF5cZmsoRRBya3xPe9-bvEntUcLZYSM4SZukl-WTlXBXyOjLE2X1dE9n_M5IZA9PmiAkxkal3JAcvV5RIfFGvZ2ssj-9DJRfDEWgFmaFXI9RWu30Woj0W982QL39o8KTDiccpOfHqnWH5lJaz4pLYnBZ5Nuz0Vd-0enm5Sh9pDEISh0by1GcJIlaBMe3gTLh122nYSLU8hjT0dCzdV0r6rD_uAB--wp_SOsyee3Pfk3pTs8XlsLJLNZQTqCcpBDwJvtW_iiS1sThbHq7MGldd0zdz1SZMAdd2ddPjShyJqnV3gP6-BvirLbQ1sdlqKvJvS3fXePOoefobd8ga43lwWsaKvn6-4L4Vdv7UmHlwDiItp40nm9QJP__WHcI8TVPNkB7UQUYMMt5N816rUHw2JsomTRjgZElmwxbZyG2fccCRZR8x7E1nmMCsAMAijN1SqLYFP0qp6f_JMJ3eqsMxKREj41F46vG2OZ8hZPD0Lc1wA2qRzPc9JFYdwp_kyN6XF_-fusA7HLxY_H5l7miKxm_iiyAfOyQNjDd8bciwbbu59c4w83cExOm63hv29euQ4qxZsiXWGE1Rw8Srk2lqWCPIzLy0)


### 주요 프로세스 설명

#### 1. 장바구니에 상품 담기
- **입력 검증**: JWT 토큰 및 요청 데이터 검증
- **재고 확인**: 상품 존재 여부, 재고 수량, 제한 수량 검증
- **에러 처리**:
  - 401: 토큰 검증 실패
  - 400: 입력 데이터 오류, 재고 부족, 제한 수량 초과
  - 404: 상품 없음

#### 2. 주문 생성
- **트랜잭션 시작**: 데이터 일관성 보장
- **장바구니 확인**: 비어있지 않은지 검증
- **재고 차감**: 비관적 락(`FOR UPDATE`)으로 동시성 제어
- **쿠폰 검증**:
  - 소유 여부 확인
  - 사용 가능 상태 확인
  - 만료 기간 확인
  - 타입별 사용 개수 제한 (CART: 최대 2개, CART_ITEM: 상품별 1개)
- **주문 생성**: 주문 및 주문 아이템 생성, 쿠폰 사용 처리, 장바구니 비우기
- **트랜잭션 커밋**: 모든 작업 완료 시 커밋

#### 3. 결제 처리
- **주문 확인**: 주문 존재 및 상태 검증 (비관적 락)
- **금액 검증**: 요청 금액과 주문 금액 일치 확인
- **포인트 결제**:
  - 잔액 확인 (비관적 락)
  - 포인트 차감 (available_point 감소, used_point 증가)
  - 거래 내역 기록 (balance_transaction)
- **결제 완료**: 결제 정보 저장, 주문 상태 업데이트
- **트랜잭션 커밋**: 결제 성공

#### 4. 결제 실패 시 보상 트랜잭션
- **트랜잭션 롤백**: 포인트 차감 롤백
- **재고 복구**: 차감된 재고 복구
- **쿠폰 복구**: 사용된 쿠폰 상태 복구
- **실패 기록**: 결제 실패 내역 저장
- **에러 응답**: 500 Internal Server Error

### 에러 코드 정리

| HTTP Status | Error Code | 설명 |
|-------------|------------|------|
| 400 | INVALID_INPUT | 입력 데이터 검증 실패 |
| 400 | INSUFFICIENT_STOCK | 재고 부족 |
| 400 | EXCEEDED_LIMITED_QUANTITY | 제한 수량 초과 |
| 400 | CART_EMPTY | 장바구니 비어있음 |
| 400 | INVALID_COUPON | 쿠폰 검증 실패 |
| 400 | INVALID_ORDER_STATE | 주문 상태 오류 |
| 400 | AMOUNT_MISMATCH | 결제 금액 불일치 |
| 400 | INSUFFICIENT_BALANCE | 포인트 잔액 부족 |
| 401 | UNAUTHORIZED | 인증 실패 |
| 404 | PRODUCT_NOT_FOUND | 상품 없음 |
| 404 | ORDER_NOT_FOUND | 주문 없음 |
| 500 | PAYMENT_FAILED | 결제 처리 실패 |

---

## ERD (Entity Relationship Diagram)

### 데이터베이스 설계 원칙
- **Soft Delete**: 물리적 삭제가 아닌 상태 변경 (state = 'DELETED')
- **No Foreign Key Constraints**: 애플리케이션 레벨에서 참조 무결성 관리
- **Audit Trail**: 모든 테이블에 생성일시, 수정일시 포함
- **정규화**: 3NF 기준 정규화

### 엔티티 관계도

> 상세한 ERD는 [dbdiagram.io](https://dbdiagram.io/d/hanghae10-e-commerce-62da44e30d66c746552aa73e)에서 확인할 수 있습니다.

```
┌──────────────────┐
│      User        │
├──────────────────┤
│ PK id            │
│    email         │
│    state         │
│    type          │
│    name          │
│    phone         │
│    available_pt  │
│    used_point    │
│    created_at    │
│    updated_at    │
└────────┬─────────┘
         │
         ├─────────────────────────┬─────────────────────────┐
         │                         │                         │
    ┌────▼──────┐         ┌───────▼────────┐       ┌───────▼────────┐
    │UserCoupon │         │     Cart       │       │    Order       │
    ├───────────┤         ├────────────────┤       ├────────────────┤
    │PK id      │         │ PK id          │       │ PK id          │
    │   user_id │         │    user_id     │       │    user_id     │
    │   coupon  │         │    user_coupon │       │    user_coupon │
    │   state   │         │    state       │       │    cart_id     │
    │   issued  │         │    created_at  │       │    state       │
    │   used_at │         │    updated_at  │       │    amount      │
    │   expires │         └────────┬───────┘       │    discount_a  │
    │   created │                  │               │    total_amt   │
    │   updated │            ┌─────▼──────────┐    │    order_num   │
    └───────────┘            │   CartItem     │    │    shipping*   │
                             ├────────────────┤    │    created_at  │
                             │ PK id          │    │    updated_at  │
                             │    cart_id     │    └────────┬───────┘
                             │    product_id  │             │
                             │    product_opt │       ┌─────▼──────────┐
                             │    user_coupon │       │   OrderItem    │
                             │    state       │       ├────────────────┤
                             │    quantity    │       │ PK id          │
                             │    created_at  │       │    order_id    │
                             │    updated_at  │       │    product_id  │
                             └────────────────┘       │    product_opt │
                                                      │    user_coupon │
┌──────────────────┐                                 │    state       │
│     Product      │                                 │    price       │
├──────────────────┤                                 │    quantity    │
│ PK id            │                                 │    discount_a  │
│    state         │                                 │    total_amt   │
│    name          │                                 │    created_at  │
│    description   │                                 │    updated_at  │
│    price         │                                 └────────────────┘
│    limited_qty   │                                          │
│    created_at    │                                    ┌─────▼─────────┐
│    updated_at    │                                    │   Payment     │
└────────┬─────────┘                                    ├───────────────┤
         │                                              │ PK id         │
         ├───────────────────┐                          │    order_id   │
    ┌────▼──────────┐   ┌────▼──────┐                  │    state      │
    │ProductOption  │   │   Stock   │                  │    method     │
    ├───────────────┤   ├───────────┤                  │    paid_amt   │
    │ PK id         │   │ PK id     │                  │    expires_at │
    │    product_id │   │    prod_id│                  │    created_at │
    │    state      │   │    prod_op│                  │    updated_at │
    │    price      │   │    avail  │                  └───────────────┘
    │    created_at │   │    sold   │
    │    updated_at │   │    memo   │         ┌──────────────────────┐
    └───────────────┘   │    created│         │ BalanceTransaction   │
                        │    updated│         ├──────────────────────┤
┌──────────────────┐   └───────────┘         │ PK id                │
│     Coupon       │                         │    user_id           │
├──────────────────┤                         │    order_id          │
│ PK id            │                         │    type              │
│    name          │                         │    amount            │
│    type          │                         │    balance_before    │
│    state         │                         │    balance_after     │
│    discount_rate │                         │    description       │
│    discount_price│                         │    created_at        │
│    total_qty     │                         │    updated_at        │
│    issued_qty    │                         └──────────────────────┘
│    begin_date    │
│    end_date      │                         ┌──────────────────────┐
│    created_at    │                         │   PopularProduct     │
│    updated_at    │                         ├──────────────────────┤
└──────────────────┘                         │ PK id                │
                                             │    product_id        │
                                             │    rank              │
                                             │    sales_count       │
                                             │    sales_amount      │
                                             │    period_start      │
                                             │    period_end        │
                                             │    created_at        │
                                             │    updated_at        │
                                             └──────────────────────┘
```

---

## 관련 문서

### 상세 설계 문서
- [요구사항 명세서](docs/api/requirements.md) - 기능적/비기능적 요구사항, 사용자 역할
- [사용자 스토리](docs/api/user-stories.md) - As a [role], I want [feature] 형식의 사용자 스토리
- [API 명세서](docs/api/api-specification.md) - RESTful API 엔드포인트 및 요청/응답 형식
- [데이터 모델](docs/api/data-model.md) - 상세한 테이블 스키마 및 제약조건
- [OpenAPI 명세](docs/api/openapi.yaml) - OpenAPI 3.0 형식의 API 문서 (Swagger UI 지원)

### 다이어그램
- [시퀀스 다이어그램](docs/api/sequence-diagram.puml) - PlantUML 형식의 주문 및 결제 프로세스
