package com.ganzithon.moyang.sheep.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
@Entity @Table(name="sheep_wallets") @Check(constraints="balance >= 0")
public class SheepWallet {
    @Id @Column(name="user_id") private Long userId;
    @Column(nullable=false) private int balance;
    @Column(nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
    protected SheepWallet(){}
    public int getBalance(){return balance;}
    public void change(int amount,LocalDateTime now){balance=Math.addExact(balance,amount);updatedAt=now;}
}
