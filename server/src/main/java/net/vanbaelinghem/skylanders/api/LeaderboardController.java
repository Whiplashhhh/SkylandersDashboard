package net.vanbaelinghem.skylanders.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService service;

    public LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    @GetMapping
    public LeaderboardPage page(@RequestParam(required = false) String game,
                                @RequestParam(required = false) String element,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) String state,
                                @RequestParam(defaultValue = "xp") String sort,
                                @RequestParam(defaultValue = "desc") String direction,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.page(game, element, category, search, state, sort, direction, page, size);
    }
}
