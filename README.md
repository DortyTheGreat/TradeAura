# TradeAura

Meteor addon that automates villager trading. Includes manual auto trade (trade is completed automatically after a manual interaction with villager) and villager-aura (autamatically clicks at villagers to perform trades)

### Download

- [26.1.2](https://github.com/DortyTheGreat/TradeAura/releases/latest)
- [1.21.11](https://github.com/DortyTheGreat/TradeAura/releases/tag/1.21.11d)
- [1.21.4](https://github.com/DortyTheGreat/TradeAura/releases/tag/1.21.4d)

> Note that only the latest Minecraft version receives updates, so earlier releases might lack some features

### Dependencies

Tested successfully with these mods, but other might work as well

- meteor-client-26.1.2-41.jar (26.1.2)
- meteor-client-1.21.11-63.jar (1.21.11)

### Usage

- Choose items that you want to buy/sell. Each 'group' supports multiple items and has rules for limiting price/quantity of the deal. You can also configure limits for trades
    - Click on villagers, trades will be proceeded automatically without manually clicking on desired trades.
        - On BUY action. Trade will happen if:
            - Emerald price of the trade is BELOW_OR_EQUAL Max Price
            - The amount of items of that specific type in your inventory is below Buy Limit (or Buy Limit is set to -1)
            - Player has emeralds (obvious)
        - On SELL action. Trade will happen if:
            - Item quantity 'price' of the trade is BELOW_OR_EQUAL Max Sell Qty
            - The amount of emeralds in your inventory is below Emerald Limit (or Buy Limit is set to -1)
            - Player has those items to sell (obvious)
- Toggle "Cancel Event" to turn off villager trading screen pop-ups
- Toggle "Villager Aura" to automatically interact with villagers in your visual range
    - Toggle "Render" to see result of automatic villager interactions

### Showcase

https://github.com/user-attachments/assets/7e2bc3a1-4222-4728-956b-aa308c9296ab
 
## License

This mod is available under the CC0 license. Feel free to use it for your own projects.

