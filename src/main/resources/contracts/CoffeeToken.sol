// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract CoffeeToken {

    string public name = "Coffee Token";
    string public symbol = "CCF";
    uint8 public decimals = 0;
    uint256 public totalSupply;

    address public owner; // Cooperativa (carteira do CryptoVault)

    mapping(address => uint256) public balanceOf;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    constructor(address _cooperativa) {
        require(_cooperativa != address(0), "Invalid cooperativa address");
        owner = _cooperativa;
        emit OwnershipTransferred(address(0), _cooperativa);
    }

    function mint(address to, uint256 amount) public onlyOwner {
        require(to != address(0), "Invalid address");
        require(amount > 0, "Amount must be > 0");

        totalSupply += amount;
        balanceOf[to] += amount;

        emit Transfer(address(0), to, amount);
    }

    function transfer(address to, uint256 amount) public returns (bool) {
        require(to != address(0), "Invalid address");
        require(balanceOf[msg.sender] >= amount, "Insufficient balance");

        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;

        emit Transfer(msg.sender, to, amount);
        return true;
    }

    function transferOwnership(address newOwner) public onlyOwner {
        require(newOwner != address(0), "Invalid address");
        emit OwnershipTransferred(owner, newOwner);
        owner = newOwner;
    }
}